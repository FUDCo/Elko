package org.elkoserver.foundation.net;

import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;

/**
 * Worker object to manage an ongoing attempt to establish an outbound TCP
 * connection, so that failed connection attempts can be retried automatically.
 */
public class ConnectionRetrier
{
    /** The host description. */
    private HostDesc myHost;

    /** Descriptive label, for trace output. */
    private String myLabel;

    /** Low-level I/O framer factory for the new connection. */
    private JSONByteIOFramerFactory myFramerFactory;

    /** Flag to stop retries. */
    private boolean myKeepTryingFlag;

    /** Message handler factory to use once connection is established. */
    private MessageHandlerFactory myActualFactory;

    /** Message handler factory to use when connection attempt is pending. */
    private MessageHandlerFactory myRetryHandlerFactory;

    /** Network manager, for making the outbound connections. */
    private NetworkManager myNetworkManager;

    /** Timeout handler to retry failed connection attempts after a while. */
    private TimeoutNoticer myRetryTimeout;

    /** Trace object for logging activity associated with the new connection */
    private Trace myTrace;

    /**
     * Attempt to initiate a connection to another host, with retry on failure.
     *
     * @param host  Description of host to connect to.
     * @param label  String describing remote connection endpoint, for
     *    diagnostic output
     * @param networkManager  Network manager to actually make the connection.
     * @param actualFactory  Application-provided message handler factory for
     *    use once connection is established.
     * @param appTrace  Application trace object for logging.
     */
    public ConnectionRetrier(HostDesc host, String label,
                             NetworkManager networkManager,
                             MessageHandlerFactory actualFactory,
                             Trace appTrace)
    {
        myHost = host;
        myLabel = label;
        myKeepTryingFlag = true;
        myTrace = appTrace.subTrace(label);
        myFramerFactory = new JSONByteIOFramerFactory(myTrace);
        myNetworkManager = networkManager;
        myActualFactory = actualFactory;
        myTrace.eventi("connecting to " + myLabel + " at " + host.hostPort());

        myRetryHandlerFactory = new MessageHandlerFactory() {
            public MessageHandler provideMessageHandler(Connection connection){
                if (connection == null) {
                    if (myKeepTryingFlag) {
                        Timer.theTimer().after(myHost.retryInterval() * 1000,
                                               myRetryTimeout);
                    }
                    return null;
                } else {
                    return myActualFactory.provideMessageHandler(connection);
                }
            }
        };

        myRetryTimeout = new TimeoutNoticer() {
            public void noticeTimeout() {
                if (myKeepTryingFlag) {
                    myTrace.eventi("retrying connection to " + myLabel +
                                   " at " + myHost.hostPort());
                    doConnect(myRetryHandlerFactory);
                }
            }
        };

        doConnect(myRetryHandlerFactory);
    }

    /**
     * Attempt to make the connection.
     */
    private void doConnect(MessageHandlerFactory outerHandlerFactory) {
        myNetworkManager.connectTCP(myHost.hostPort(), outerHandlerFactory,
                                    myFramerFactory, myTrace);
    }
    
    /**
     * Stop retrying this connection.
     */
    public void giveUp() {
        myKeepTryingFlag = false;
    }
}

