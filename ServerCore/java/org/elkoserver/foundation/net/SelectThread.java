package org.elkoserver.foundation.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Callable;
import java.util.Iterator;
import org.elkoserver.foundation.run.Queue;
import org.elkoserver.util.trace.Trace;

import scalablessl.SSLSelector;
import javax.net.ssl.SSLContext;

/**
 * A thread for doing all network I/O operations (send, receive, accept) using
 * non-blocking Channels.  Requests to open listeners, connect to remote hosts,
 * and send messages on connections are all fed to this thread via a queue.
 * This thread in turn feeds received input events to the run queue.
 */
class SelectThread extends Thread {
    /** Selector to await available I/O opportunities. */
    private Selector mySelector;

    /** Queue of unserviced I/O requests. */
    private Queue myQueue;

    /** Network manager for this server */
    private NetworkManager myMgr;

    /**
     * Constructor.
     *
     * @param mgr  Network manager for this server.
     * @param sslContext  SSL context to use, if supporting SSL, else null
     */
    SelectThread(NetworkManager mgr, SSLContext sslContext) {
        super("Elko Select");
        myMgr = mgr;
        myQueue = new Queue();
        try {
            if (sslContext != null) {
                mySelector = SSLSelector.open(sslContext);
            } else {
                mySelector = Selector.open();
            }
            start();
        } catch (IOException e) {
            Trace.comm.errorm("failed to start SelectThread", e);
        }
    }
    
    /**
     * The body of the thread.  Responsible for dequeueing I/O requests and
     * acting upon them, and for selecting over the currently open set of
     * sockets.
     */
    public void run() {
        if (Trace.comm.debug) {
            Trace.comm.debugm("select thread running");
        }
        while (true) {
            try {
                int selectedCount = mySelector.select();
                if (Trace.comm.debug) {
                    Trace.comm.debugm("select() returned with count=" +
                                      selectedCount);
                }
                
                Object workToDo = myQueue.optDequeue();
                while (workToDo != null) {
                    if (workToDo instanceof Listener) {
                        Listener listener = (Listener) workToDo;
                        listener.register(this, mySelector);
                        if (Trace.comm.debug) {
                            Trace.comm.debugm(
                                "select thread registers listener " +
                                listener);
                        }
                    } else if (workToDo instanceof Callable) {
                        Callable<?> thunk = (Callable<?>) workToDo;
                        thunk.call();
                    } else {
                        Trace.comm.errorm("mystery object on select queue: " +
                                          workToDo);
                    }
                    workToDo = myQueue.optDequeue();
                }
                
                if (selectedCount > 0) {
                    Iterator<SelectionKey> iter =
                        mySelector.selectedKeys().iterator();
                    int actualCount = 0;
                    while (iter.hasNext()) {
                        ++actualCount;
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isValid() && key.isAcceptable()) {
                            Listener listener = (Listener) key.attachment();
                            if (Trace.comm.debug) {
                                Trace.comm.debugm("select has accept for " +
                                                  listener);
                            }
                            listener.doAccept();
                        }
                        if (key.isValid() && key.isReadable()) {
                            TCPConnection connection =
                                (TCPConnection) key.attachment();
                            if (Trace.comm.debug) {
                                Trace.comm.debugm("select has read for " +
                                                  connection);
                            }
                            connection.doRead();
                        }
                        if (key.isValid() && key.isWritable()) {
                            TCPConnection connection =
                                (TCPConnection) key.attachment();
                            connection.wakeupSelectForWrite();
                            if (Trace.comm.debug) {
                                Trace.comm.debugm("select has write for " +
                                                  connection);
                            }
                            connection.doWrite();
                        }
                    }
                    if (Trace.comm.debug) {
                        if (actualCount > 0) {
                            Trace.comm.debugm("select thread selected " +
                                              actualCount + "/" +
                                              selectedCount + " I/O sources");
                        }
                    }
                }
            } catch (Throwable e) {
                Trace.comm.errorm("select failed", e);
            }
        }
    }

    /**
     * Make a new outbound TCPConnection to another host on the net.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     *    received on the new connection.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param remoteAddr  Host name and port number to connect to.
     * @param trace  Trace object to use for activity on the new connectoin.
     */
    void connect(final MessageHandlerFactory handlerFactory,
                 final ByteIOFramerFactory framerFactory,
                 final String remoteAddr, final Trace trace)
    {
        myQueue.enqueue(new Callable<Object>() {
            public Object call() {
                try {
                    NetAddr remoteNetAddr = new NetAddr(remoteAddr);
                    InetSocketAddress socketAddress =
                        new InetSocketAddress(remoteNetAddr.inetAddress(),
                                              remoteNetAddr.getPort());
                    Trace.comm.eventi("connecting to " + remoteNetAddr);
                    SocketChannel channel = SocketChannel.open(socketAddress);
                    newChannel(handlerFactory, framerFactory, channel, false,
                               trace);
                } catch (IOException e) {
                    myMgr.connectionCount(-1);
                    Trace.comm.errorm("unable to connect to " + remoteAddr +
                                      ": " + e);
                    handlerFactory.provideMessageHandler(null);
                }
                return null;
            }
        });
        mySelector.wakeup();
    }

    /**
     * Begin listening for inbound TCP connections on some port.
     *
     * @param localAddress  Host name and port to listen for connections on.
     * @param handlerFactory  Message handler factory to provide the handlers
     *    for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param secure  If true, use SSL.
     * @param portTrace  Trace object for logging activity associated with this
     *   port & its connections
     */
    Listener listen(String localAddress, MessageHandlerFactory handlerFactory,
                    ByteIOFramerFactory framerFactory, boolean secure,
                    Trace portTrace)
        throws UnknownHostException, IOException
    {
        Listener listener =
            new Listener(localAddress, handlerFactory,
                         framerFactory, myMgr, secure, portTrace);
        myQueue.enqueue(listener);
        mySelector.wakeup();
        return listener;
    }

    /**
     * Handle the establishment of a new connection as the result of an
     * outbound connection made to another host or an accept operation by a
     * listener.
     *
     * @param handlerFactory  Message handler factory to provide the handlers
     *    for the new connection.
     * @param framerFactory  Byte I/O framer factory for the new connection.
     * @param channel  The new channel for the new connection.
     * @param secure  If true, this will be an SSL connnection.
     * @param trace  Trace object to use with this new connection.
     */
    void newChannel(MessageHandlerFactory handlerFactory,
                    ByteIOFramerFactory framerFactory,
                    SocketChannel channel, boolean isSecure, Trace trace)
    {
        try {
            channel.configureBlocking(false);
            SelectionKey key =
                channel.register(mySelector, SelectionKey.OP_READ);
            key.attach(new TCPConnection(handlerFactory, framerFactory,
                channel, key, this, myMgr, isSecure, trace));
        } catch (ClosedChannelException e) {
            myMgr.connectionCount(-1);
            handlerFactory.provideMessageHandler(null);
            Trace.comm.errorm("channel closed before it could be used", e);
        } catch (IOException e) {
            myMgr.connectionCount(-1);
            handlerFactory.provideMessageHandler(null);
            Trace.comm.errorm("trouble opening TCPConnection for channel", e);
            try {
                channel.close();
            } catch (IOException e2) {
                /* So if close fails, we're supposed to do what?  Close? */
            }
        }
    }

    /**
     * Notify this thread that a connection now has messages queued ready for
     * transmission.
     *
     * @param connection  The connection that has messages ready to send.
     */
    void readyToSend(TCPConnection connection) {
        if (Trace.comm.debug) {
            Trace.comm.debugm(connection + " ready to send");
        }
        myQueue.enqueue(connection);
        mySelector.wakeup();
    }
}
