package org.elkoserver.foundation.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.elkoserver.util.trace.Trace;

import scalablessl.SSLServerSocketChannel;

/**
 * A listener for new inbound TCP connections to some server port.
 */
class Listener {
    /** The address to listen on, or null for the default address. */
    private InetAddress myOptIP = null;

    /** Address string for the requested listen address. */
    private String myLocalAddress;

    /** The message handler factory for connections to this listener's port. */
    private MessageHandlerFactory myHandlerFactory;

    /** Low-level I/O framer factory for connections to this listener's port */
    private ByteIOFramerFactory myFramerFactory;

    /** The channel doing the actual listening. */
    private ServerSocketChannel myChannel;

    /** The thread that does select calls on behalf of this listener. */
    private SelectThread mySelectThread;

    /** Listener is expecting SSL connections. */
    private boolean amSecure;

    /** Network manager for this server. */
    private NetworkManager myMgr;

    /** Trace object this listener should use. */
    private Trace myTrace;

    /**
     * Constructor.
     *
     * This must *not* be called from inside the select thread.
     *
     * @param localAddress  The local address and port to will listen on.
     * @param handlerFactory  Message handler factory to provide the handlers
     *    for connections made to this port.
     * @param framerFactory  Byte I/O framer factory for new connections.
     * @param mgr  Network manager for this server.
     * @param secure  If true, use SSL.
     * @param portTrace  Trace object for logging activity associated with this
     *   port & its connections
     */
    Listener(String localAddress, MessageHandlerFactory handlerFactory,
             ByteIOFramerFactory framerFactory, NetworkManager mgr,
             boolean secure, Trace portTrace)
        throws UnknownHostException, IOException
    {
        mySelectThread = null;
        myHandlerFactory = handlerFactory;
        myFramerFactory = framerFactory;
        myMgr = mgr;
        myTrace = portTrace;
        amSecure = secure;

        myLocalAddress = localAddress;
        NetAddr netAddr = new NetAddr(localAddress);
        myOptIP = netAddr.inetAddress();
        int localPort = netAddr.getPort();
        SSLServerSocketChannel asSSL = null;

        if (secure) {
            asSSL = SSLServerSocketChannel.open(mgr.sslContext());
            myChannel = asSSL;
            asSSL.socket().setNeedClientAuth(false);
        } else {
            myChannel = ServerSocketChannel.open();
        }
        myChannel.configureBlocking(false);
        myChannel.socket().bind(new InetSocketAddress(myOptIP, localPort), 50);
    }

    /**
     * Do an accept() operation, given that the selector has indicated that
     * this can happen without blocking.
     *
     * This *must* be called from inside the select thread.
     */
    void doAccept() {
        try {
            SocketChannel newChannel = myChannel.accept();
            if (newChannel != null) {
                myMgr.connectionCount(1);
                mySelectThread.newChannel(myHandlerFactory, myFramerFactory,
                                          newChannel, amSecure, myTrace);
            } else {
                myTrace.usagem("accept returned null socket, ignoring");
            }
        } catch (IOException e) {
            myTrace.warningm("accept on " + myLocalAddress +
                             " failed -- closing listener, IOException: " +
                             e.getMessage());
            try {
                myChannel.close();
            } catch (IOException e2) {
                /* Yeah, like I could do something about it... */
            }
        }
    }

    /**
     * Get the address on which this listener is listening.
     *
     * @return this listener's listen address.
     */
    NetAddr listenAddress() {
        return new NetAddr(myOptIP, myChannel.socket().getLocalPort());
    }

    /**
     * Register this listener with a selector.
     *
     * This *must* be run from inside the select thread.
     *
     * @param selectThread  The select thread that is managing this listener
     * @param selector  The selector to register with
     */
    void register(SelectThread selectThread, Selector selector)
        throws ClosedChannelException
    {
        mySelectThread = selectThread;
        SelectionKey key =
            myChannel.register(selector, SelectionKey.OP_ACCEPT);
        key.attach(this);
    }
}
