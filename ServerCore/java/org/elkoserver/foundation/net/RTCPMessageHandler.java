package org.elkoserver.foundation.net;

import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Message handler for RTCP requests wrapping a message stream.
 */
class RTCPMessageHandler implements MessageHandler {
    /** The connection this handler handles messages for. */
    private Connection myConnection;

    /** The factory that created this handler, which also contains much of the
        handler implementation logic. */
    private RTCPMessageHandlerFactory myFactory;

    /** Timeout for kicking off users who connect and then don't do anything */
    private Timeout myStartupTimeout;

    /** Flag that startup timeout has tripped, to detect late messages. */
    private boolean myStartupTimeoutTripped;

    /**
     * Constructor.
     *
     * @param connection  The connection this is to be a message handler for.
     * @param factory  The factory what created this.
     * @param startupTimeoutInterval  How long a new connection is given to do
     *    something before kicking them off.
     */
    RTCPMessageHandler(Connection connection,
                       RTCPMessageHandlerFactory factory,
                       int startupTimeoutInterval)
    {
        myConnection = connection;
        myFactory = factory;
        myStartupTimeoutTripped = false;
        myStartupTimeout =
            Timer.theTimer().after(
                startupTimeoutInterval,
                new TimeoutNoticer() {
                    /* Kick the user off if they haven't yet done anything. */
                    public void noticeTimeout() {
                        if (myStartupTimeout != null) {
                            myStartupTimeout = null;
                            myStartupTimeoutTripped = true;
                            myConnection.close();
                        }
                    }
                });
    }

    /**
     * Receive notification that the connection has died.
     *
     * In this case, the connection is a TCP connection supporting RTCP, so it
     * doesn't really matter that it died.  Gratuitous TCP connection drops are
     * actually considered normal in the RTCP world.
     *
     * @param connection The (RTCP over TCP) connection that died.
     * @param reason  Why it died.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        myFactory.tcpConnectionDied(connection, reason);
    }

    /**
     * Handle an incoming message from the connection.
     *
     * Since this is an RTCP connection, the message (as parsed by the
     * RTCPRequest framer) will be an RTCP request.  The nature of the request
     * determines what it is from the perspective of the higher level message
     * stream being supported.
     *
     * @param connection  The connection the message was received on.
     * @param rawMessage   The message that was received.  This must be an
     *    instance of RTCPRequest.
     */
    public void processMessage(Connection connection, Object rawMessage) {
        if (myStartupTimeoutTripped) {
            /* They were kicked off for lacktivity, so ignore the message. */
            return;
        }
        if (myStartupTimeout != null) {
            myStartupTimeout.cancel();
            myStartupTimeout = null;
        }

        RTCPRequest message = (RTCPRequest) rawMessage;
        if (Trace.comm.debug && Trace.ON) {
            Trace.comm.debugm(connection + " " + message);
        }
        
        switch (message.verb()) {
            case RTCPRequest.VERB_START:
                myFactory.doStart(connection);
                break;
            case RTCPRequest.VERB_RESUME:
                myFactory.doResume(connection, message.sessionID(),
                                   message.clientRecvSeqNum());
                break;
            case RTCPRequest.VERB_ACK:
                myFactory.doAck(connection, message.clientRecvSeqNum());
                break;
            case RTCPRequest.VERB_MESSAGE:
                myFactory.doMessage(connection, message);
                break;
            case RTCPRequest.VERB_END:
                myFactory.doEnd(connection);
                break;
            case RTCPRequest.VERB_ERROR:
                myFactory.doError(connection, message.error(), null);
                break;
        }
    }
}

