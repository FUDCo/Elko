package org.elkoserver.foundation.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import org.elkoserver.foundation.run.Queue;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.util.trace.Trace;

/**
 * An implementation of {@link Connection} that manages a non-blocking TCP
 * connection to a single remote host.
 */
public class TCPConnection
    extends ConnectionBase
    implements MessageReceiver, Callable<Object>
{
    /** Queue of unencoded outbound messages. */
    private Queue myOutputQueue;

    /** Framer to perform low-level message conversion. */
    private ByteIOFramer myFramer;

    /** Buffer holding actual output bytes. */
    private ByteBuffer myOutputBuffer;

    /** Thread that does blocking select operations for this connection. */
    private SelectThread mySelectThread;
    
    /** Channel for sending and receiving over. */
    private SocketChannel myChannel;

    /** Selection key for testing the readability and writability of
        'myChannel'. */
    SelectionKey myKey;

    /** Printable form of the address this connection is connected to. */
    private String myRemoteAddr;
    
    /** Time an inbound message was last received. */
    private long myLastNetActivity = System.currentTimeMillis();

    /** Default size of input buffer. */
    private static final int INPUT_BUFFER_SIZE = 2048;

    /** Buffer receiving actual input bytes. */
    private ByteBuffer myInputBuffer;

    /** Monitor lock for synching with the select thread. */
    private Object myWakeupLock = new Object();

    /** Flag to trigger select thread to look for write opportunities. */
    private boolean amNeedingToWakeupSelect;

    /** Flag that is true until the connection is closed. */
    private boolean amOpen;

    /** Network manager for this server. */
    private NetworkManager myMgr;

    /** Flag that this is an SSL connection. */
    private boolean amSecure;

    /** Trace object to use for logging associated with this connection. */
    private Trace myTrace;

    /** Empty buffer, for empty sends used to pump SSL logic. */
    private static final ByteBuffer theEmptyBuffer =
        ByteBuffer.wrap(new byte[0]);

    /**
     * Constructor.
     *
     * This constructor *must* be called from inside the select thread.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     *    received on this connection.
     * @param framerFactory  Byte I/O framer factory for the connection.
     * @param channel  Channel to the TCP connection proper.
     * @param key  Selection key for reads and writes over 'channel'.
     * @param selectThread  Select thread that is managing this connection.
     * @param mgr  Network manager for this server.
     * @param isSecure  If true, this is an SSL connection.
     * @param trace  Trace object to use for this connection.
     */
    TCPConnection(MessageHandlerFactory handlerFactory,
                  ByteIOFramerFactory framerFactory, SocketChannel channel,
                  SelectionKey key, SelectThread selectThread,
                  NetworkManager mgr, boolean isSecure, Trace trace)
        throws IOException
    {
        super(mgr);
        amSecure = isSecure;
        myTrace = trace;
        wakeupSelectForWrite();
        myChannel = channel;
        myKey = key;
        myMgr = mgr;
        amOpen = true;
        channel.configureBlocking(false);
        Socket socket = channel.socket();
        socket.setSoLinger(true, 0);
        socket.setReuseAddress(true);
        myRemoteAddr = socket.getInetAddress().getHostAddress() + ":" +
            socket.getPort();
        myInputBuffer = ByteBuffer.wrap(new byte[INPUT_BUFFER_SIZE]);
        myFramer = framerFactory.provideFramer(this, label());
        myOutputBuffer = null;
        myOutputQueue = new Queue();
        mySelectThread = selectThread;
        enqueueHandlerFactory(handlerFactory);
        if (myTrace.event && Trace.ON) {
            myTrace.eventi(this + " new connection from " + myRemoteAddr);
        }
    }

    /**
     * Invoked from the selector thread's work queue when the selector is ready
     * to do a write.  If this connection has pending output to send, adjusts
     * the selection key so that it will then attend to the availability of
     * write opportunities when select() is called.
     */
    public Object call() {
        if (myOutputQueue.hasMoreElements() && myKey.isValid()) {
            myKey.interestOps(myKey.interestOps() | SelectionKey.OP_WRITE);
            if (myTrace.debug && Trace.ON) {
                myTrace.debugm(this + " set selectkey Read/Write");
            }
        }
        if (myTrace.debug) {
            myTrace.debugm("select thread interested in writes on " + this);
        }
        return null;
    }

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    public void close() {
        if (myTrace.debug && Trace.ON) {
            myTrace.debugm(this + " close");
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
     * been sent and the channel closed.
     *
     * @param reason  A Throwable describing why the connection is closing.
     */
    private void closeIsDone(Throwable reason) {
        try {
            myChannel.close();
        } catch (IOException e) {
            /* Throwing an IOException on connection close has got to be one of
               the stupidest things -- what're you gonna do about it? Close the
               connection? */
            myTrace.debugm(this + " ignoring IOException on close");
        }
        myKey.attach(null);
        myMgr.connectionCount(-1);
        if (myTrace.event && Trace.ON) {
            myTrace.eventi(this + " died: " + reason);
        }
        Object message = myOutputQueue.optDequeue();
        while (message != null) {
            if (message instanceof Releasable) {
                ((Releasable) message).release();
            }
            message = myOutputQueue.optDequeue();
        }
        connectionDied(reason);
    }

    /**
     * Do a read() operation, given that the selector has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    void doRead() {
        try {
            int count;
            do {
                count = myChannel.read(myInputBuffer);

                if (count < 0) {
                    /* EOF: cease to be interested in reads, then throw EOF. */
                    myKey.interestOps(myKey.interestOps() & ~SelectionKey.OP_READ);
                    throw new EOFException();
                } else {
                    /* Data read: give bytes to framer, then recycle the buffer. */
                    if (count > 0) {
                        myFramer.receiveBytes(myInputBuffer.array(),
                                              myInputBuffer.position());
                        myInputBuffer.clear();
                    } else {
                        if (myTrace.event && Trace.ON) {
                            myTrace.debugm(this + " zero length read");
                        }
                    }
                }
            } while (count > 0 && amSecure);
        } catch (Throwable t) {
            /* If anything bad happens during read, the connection is dead. */
            if (myTrace.debug) {
                myTrace.debugm(this + " caught exception", t);
            }
            if (t instanceof EOFException) {
                myTrace.eventi(this + " remote disconnect");
            } else if (t instanceof IOException) {
                myTrace.usagei(this + " IOException: " + t.getMessage());
            } else {
                myTrace.errorm(this + " Error", t);
            }
            close();
            /* Kill it immediately: if the connection is dead, the write queue
               will never be processed, so orderly close will never finish. */
            closeIsDone(t);
            Runner.throwIfMandatory(t);
        }
    }

    /**
     * Do a write() operation, given that the selector has indicated that this
     * can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    void doWrite() { /* not Dudley */
        Exception closeException = null;

        try {
            if (myOutputBuffer == null) {
                Object message = myOutputQueue.optDequeue();
                if (message == theCloseMarker) {
                    closeException = new ConnectionCloseException(
                        "Normal TCP connection close");
                } else if (message != null) {
                    myOutputBuffer =
                        ByteBuffer.wrap(myFramer.produceBytes(message));
                    if (message instanceof Releasable) {
                        ((Releasable) message).release();
                    }
                }
            }
            if (myOutputBuffer != null) {
                int before = myOutputBuffer.remaining();
                int wrote = myChannel.write(myOutputBuffer);
                if (myTrace.event && Trace.ON) {
                    myTrace.debugm(this + " wrote " + wrote + " bytes of " + before);
                }
                if (myOutputBuffer.remaining() == 0) {
                    myOutputBuffer = null;
                }
            } else if (amSecure) {
                /* ScalableSSL sometimes requires us to do empty writes to pump
                   SSL protocol handshaking. */
                if (myTrace.event && Trace.ON) {
                    myTrace.debugm(this + " SSL empty write");
                }
                myChannel.write(theEmptyBuffer);
            }
        } catch (IOException e) {
            myTrace.usagem(this + " IOException: " + e.getMessage());
            closeException = e;
        }
        if (closeException != null) {
            closeIsDone(closeException);
        } else if (myOutputBuffer == null) {
            if (!myOutputQueue.hasMoreElements()) {
                myKey.interestOps(myKey.interestOps() &~SelectionKey.OP_WRITE);
                if (myTrace.debug && Trace.ON) {
                    myTrace.debugm(this + " set selectkey ReadOnly");
                }
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
        if (myTrace.verbose && Trace.ON) {
            myTrace.verbosem("enqueue " + message);
        }

        /* If the connection is going away, the message can be discarded. */
        if (amOpen) {
            myOutputQueue.enqueue(message);
            boolean doWakeup;
            synchronized (myWakeupLock) {
                doWakeup = amNeedingToWakeupSelect;
                amNeedingToWakeupSelect = false;
            }
            if (doWakeup) {
                mySelectThread.readyToSend(this);
            }
        } else {
            if (message instanceof Releasable) {
                ((Releasable) message).release();
            }
        }
    }

    /**
     * Test if this connection is available for writes.
     *
     * The idea here is to get an approximate sense, when arbitrating among a
     * series of alternate possible connections to transmit something over.  Do
     * not do anything that depends for its correctness on the answer returned
     * by this method being accurate.
     *
     * @return true if this connection appears to be writable, false if not.
     */
    public boolean isWritable() {
        try {
            return
                amOpen &&
                !myOutputQueue.hasMoreElements() &&
                (myOutputBuffer == null || !myOutputBuffer.hasRemaining());
        } catch (NullPointerException e) {
            /* We are looking at myOutputBuffer from outside the thread that is
               actually entitled to be looking at it, so it is possible that
               the variable will be non-null when we test it for null and then
               null a moment later when we try to invoke hasRemaining() on
               it. Since the purpose of this method is only to give an
               approximate take on the writabilty of the connection, we can
               declare that if this NPE happens then the buffer is now null and
               hence writable.  We could be more disciplined about locking and
               such, but it would be huge increase in thread madness with no
               particular benefit gained as a consequence.  So this little bit
               of meatball hackery seems like it's actually the least dumb
               thing to do here (aside from arranging to not want to ask the
               "is it writable?"  question in the first place, of course; but
               that would be even more work.) */
            return true;
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
     * This is called from the select thread. Consequently, it does not
     * actually process the message but simply puts it on the run queue.
     *
     * @param message the incoming message.
     */
    public void receiveMsg(Object message) {
        myLastNetActivity = System.currentTimeMillis();
        enqueueReceivedMessage(message);
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    public void sendMsg(Object message) {
        if (myTrace.debug && Trace.ON) {
            myTrace.debugm(this + " enqueueing message: " + message);
        }
        enqueueSentMessage(message);
    }

    /**
     * Obtain a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    public String toString() {
        return "TCP(" + id() + ")";
    }

    /**
     * Mark this connection as needing to notify the select thread the next
     * time messages are enqueued on the connection for sending.
     */
    void wakeupSelectForWrite() {
        synchronized (myWakeupLock) {
            amNeedingToWakeupSelect = true;
        }
    }
}
