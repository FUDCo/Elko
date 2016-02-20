package org.elkoserver.foundation.net.zmq;

import org.elkoserver.foundation.net.ByteIOFramer;
import org.elkoserver.foundation.net.ByteIOFramerFactory;
import org.elkoserver.foundation.net.ConnectionBase;
import org.elkoserver.foundation.net.ConnectionCloseException;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.MessageReceiver;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.run.Queue;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.foundation.run.Thunk;
import org.elkoserver.util.trace.Trace;
import org.zeromq.ZMQ;
import java.io.IOException;

/**
 * An implementation of {@link org.elkoserver.foundation.net.Connection} that
 * manages a message connection over a ZeroMQ socket.
 */
public class ZeroMQConnection
    extends ConnectionBase
    implements MessageReceiver, Thunk
{
    /** Queue of unencoded outbound messages. */
    private Queue myOutputQueue;

    /** Framer to perform low-level message conversion. */
    private ByteIOFramer myFramer;

    /** ZMQ polling thread this connection belongs to. */
    ZeroMQThread myThread;

    /** Socket for sending messages to. */
    private ZMQ.Socket mySocket;

    /** Printable form of the address this connection is connected to. */
    private String myRemoteAddr;

    /** Time an inbound message was last received. */
    private long myLastNetActivity = System.currentTimeMillis();

    /** Monitor lock for synching with the ZMQ thread. */
    private Object myWakeupLock = new Object();

    /** Flag to trigger ZMQ thread to look for write opportunities. */
    private boolean amNeedingToWakeupThread;

    /** Flag indicating send vs. receive: true=>send, false=>receive. */
    private boolean amSendMode;

    /** Flag that is true until the connection is closed. */
    private boolean amOpen;

    /** Network manager for this server. */
    private NetworkManager myMgr;

    /** A cached array of two newlines, in case we need to add framing to
        an unframed received ZMQ blob. */
    private final static byte[] NEWLINES = { '\n', '\n' };

    ZeroMQConnection(MessageHandlerFactory handlerFactory,
                     ByteIOFramerFactory framerFactory,
                     ZMQ.Socket socket,
                     boolean isSendMode,
                     ZeroMQThread thread,
                     NetworkManager networkMgr,
                     String remoteAddr)
    {
        super(networkMgr);
        wakeupThreadForWrite();
        mySocket = socket;
        myMgr = networkMgr;
        amOpen = true;
        amSendMode = isSendMode;
        myRemoteAddr = remoteAddr;
        myFramer = framerFactory.provideFramer(this, label());
        myOutputQueue = new Queue();
        myThread = thread;
        enqueueHandlerFactory(handlerFactory);
        if (Trace.comm.event && Trace.ON) {
            Trace.comm.eventm(this + " new ZMQ connection with " + remoteAddr);
        }
    }

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    public void close() {
        if (Trace.comm.debug && Trace.ON) {
            Trace.comm.debugm(this + " close");
        }

        /* Enqueue a special object to mark the end of the outgoing message
         * stream.  Output queue handler will call closeIsDone() when it pulls
         * this marker off the queue, which will be right after the last
         * message goes out.
         */
        if (amOpen) {
            enqueueSentMessage(theCloseMarker);
            amOpen = false;
        }
    }

    /**
     * Cleanup and notify the message handler that all queued messages have
     * been sent and the socket closed.
     *
     * @param reason  A Throwable describing why the connection is closing.
     */
    private void closeIsDone(Throwable reason) {
        mySocket.close();
        myMgr.connectionCount(-1);
        if (Trace.comm.event && Trace.ON) {
            Trace.comm.eventm(this + " died: " + reason);
        }
        connectionDied(reason);
    }

    /**
     * Do something when there is an error on a socket.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    void doError() {
    }

    /**
     * Do a read operation, given that the poller has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    void doRead() {
        try {
            byte data[] = mySocket.recv(0);
            if (data != null) {
                int length = data.length;
                while (length > 0 && data[length - 1] == 0) {
                    --length;
                }
                /* XXX Some message sources (I'm looking at you, ZWatcher) put
                   crud in front of the message; assuming said crud is purely
                   alphanumeric (a weak and dangerous assumption, but true in
                   the motivating case), we can strip it off by simply scanning
                   for the brace character that starts the message.  Yes, this
                   is a hack; we'll refactor later. */
                int start = 0;
                while (data[start] != '{' && start < length) {
                    ++start;
                }
                if (start > 0) {
                    length -= start;
                    byte[] newData = new byte[length];
                    System.arraycopy(data, start, newData, 0, length);
                    data = newData;
                }
                /* XXX end of hack */
                int nlCount = 0;
                while (length > 0 && data[length - 1] == '\n') {
                    --length;
                    ++nlCount;
                }
                if (length > 0) {
                    if (nlCount < 2) {
                        myFramer.receiveBytes(data, length);
                        myFramer.receiveBytes(NEWLINES, 2);
                    } else {
                        myFramer.receiveBytes(data, length + 2);
                    }
                }
            } else {
                throw new ConnectionCloseException("Null ZMQ recv result");
            }
        } catch (Throwable t) {
            Trace.comm.eventm(this + " problem: " + t);
            close();
            closeIsDone(t);
            Runner.throwIfMandatory(t);
        }
    }

    /**
     * Do a write operation, given that the poller has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the ZMQ thread.
     */
    void doWrite() { /* not Dudley */
        Exception closeException = null;

        try {
            Object message = myOutputQueue.optDequeue();
            byte[] outBytes = null;
            if (message == theCloseMarker) {
                closeException =
                   new ConnectionCloseException("Normal ZMQ connection close");
            } else if (message != null) {
                outBytes = myFramer.produceBytes(message);
            }
            if (outBytes != null) {
                mySocket.send(outBytes, 0);
            }
        } catch (IOException e) {
            Trace.comm.usagem(this + " IOException: " + e.getMessage());
            closeException = e;
        }
        if (closeException != null) {
            closeIsDone(closeException);
        } else if (!myOutputQueue.hasMoreElements()) {
            myThread.unwatchSocket(mySocket);
            if (Trace.comm.debug && Trace.ON) {
                Trace.comm.debugm(this + " set poll off");
            }
        }
    }

    /**
     * Enqueue a message for output.
     *
     * @param message  The message to put on the queue; normally this will be a
     *    String, but this is not required.
     */
    private void enqueueSentMessage(Object message) {
        if (Trace.comm.verbose && Trace.ON) {
            Trace.comm.verbosem("enqueue " + message);
        }

        /* If the connection is going away, the message can be discarded. */
        if (amOpen) {
            myOutputQueue.enqueue(message);
            boolean doWakeup;
            synchronized (myWakeupLock) {
                doWakeup = amNeedingToWakeupThread;
                amNeedingToWakeupThread = false;
            }
            if (doWakeup) {
                myThread.readyToSend(this);
            }
        }
    }

    /**
     * Get a short string for labelling this connection in log entries.
     *
     * @return a label for this connection
     */
    public String label() {
        return toString();
    }

    /**
     * Receive an incoming message from the remote end.
     *
     * This is called from the ZMQ thread. Consequently, it does not
     * actually process the message but simply puts it on the run queue.
     *
     * @param message the incoming message.
     */
    public void receiveMsg(Object message) {
        myLastNetActivity = System.currentTimeMillis();
        enqueueReceivedMessage(message);
    }

    /**
     * Invoked from the ZMQ thread's work queue when the poller is ready to do
     * a write.  If this connection has pending output to send, adjusts the
     * poll set so that it will then attend to the availability of write
     * opportunities when poll() is called.
     */
    public Object run() {
        if (myOutputQueue.hasMoreElements()) {
            myThread.watchSocket(mySocket, ZMQ.Poller.POLLOUT);
            if (Trace.comm.debug && Trace.ON) {
                Trace.comm.debugm(this + " set poller for write");
            }
        }
        if (Trace.comm.debug) {
            Trace.comm.debugm("ZMQ thread interested in writes on " + this);
        }
        return null;
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    public void sendMsg(Object message) {
        if (amSendMode) {
            if (Trace.comm.debug && Trace.ON) {
                Trace.comm.debugm(this + " enqueueing message");
            }
            enqueueSentMessage(message);
        } else {
            Trace.comm.errorm(this +
                              " send on a receive-only connection: " +
                              message);
        }
    }

    /**
     * Obtain the socket this connection sits on top of.
     *
     * @return this connection's socket.
     */
    ZMQ.Socket socket() {
        return mySocket;
    }

    /**
     * Obtain a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    public String toString() {
        return "ZMQ(" + id() + ")";
    }

    /**
     * Mark this connection as needing to notify the ZMQ thread the next time
     * messages are enqueued on the connection for sending.
     */
    void wakeupThreadForWrite() {
        synchronized (myWakeupLock) {
            amNeedingToWakeupThread = true;
        }
    }
}
