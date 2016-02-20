package org.elkoserver.foundation.net;

import java.util.HashMap;
import java.util.Map;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a series of RTCP requests.
 *
 * <p>RTCP builds a message channel that can span a series of successive TCP
 * connections, allowing a session to be interrupted by connection failure and
 * then resumed without loss of session state.
 */
class RTCPMessageHandlerFactory implements MessageHandlerFactory {
    /** The message handler factory for the messages embedded in the composite
        stream. */
    private MessageHandlerFactory myInnerFactory;

    /** Trace object for logging message traffic. */
    private Trace trMsg;

    /** Table of current sessions, indexed by session ID. */
    private Map<String, RTCPSessionConnection> mySessions;

    /** Table of current sessions, indexed by TCP connection. */
    private Map<Connection, RTCPSessionConnection> mySessionsByConnection;

    /** Network manager for this server */
    private NetworkManager myManager;

    /** Time an RTCP session can sit idle before being killed, milliseconds. */
    private int mySessionInactivityTimeout;

    /** Like mySessionInactivityTimeout, but when in debug mode. */
    private int myDebugSessionInactivityTimeout;

    /** Time a session can sit disconnected before being killed, in ms. */
    private int mySessionDisconnectedTimeout;

    /** Like mySessionDisconnectedTimeout, but when in debug mode. */
    private int myDebugSessionDisconnectedTimeout;

    /** Volume of message backlog an RTCP session can tolerate before being
        killed, in characters. */
    private int mySessionBacklogLimit;

    /** Default inactivity timeout if none is explicitly given, in seconds. */
    static final int DEFAULT_SESSION_INACTIVITY_TIMEOUT = 60;

    /** Default disconnected timeout if none is explicitly given, in secs. */
    static final int DEFAULT_SESSION_DISCONNECTED_TIMEOUT = 30;

    /** Default message backlog limit if none explicitly given, in chars. */
    static final int DEFAULT_SESSION_BACKLOG_LIMIT = 64000;

    /**
     * Each RTCP message handler wraps an application-level message handler,
     * which is the entity that will actually process the messages extracted
     * from the RTCP requests, so the RTCP message handler factory needs to
     * wrap the application-level message handler factory.
     *
     * @param innerFactory  The application-level message handler factory that
     *   is to be wrapped by this.
     * @param msgTrace   Trace object for logging message traffic
     * @param manager  Network manager for this server.
     */
    RTCPMessageHandlerFactory(MessageHandlerFactory innerFactory,
                              Trace msgTrace,
                              NetworkManager manager)
    {
        myInnerFactory = innerFactory;
        mySessions = new HashMap<String, RTCPSessionConnection>();
        mySessionsByConnection =
            new HashMap<Connection, RTCPSessionConnection>();
        trMsg = msgTrace;
        myManager = manager;

        BootProperties props = manager.props();
        mySessionInactivityTimeout =
            props.intProperty("conf.comm.rtcptimeout",
                              DEFAULT_SESSION_INACTIVITY_TIMEOUT) * 1000;
        myDebugSessionInactivityTimeout =
            props.intProperty("conf.comm.rtcptimeout.debug",
                              DEFAULT_SESSION_INACTIVITY_TIMEOUT) * 1000;
        mySessionDisconnectedTimeout =
            props.intProperty("conf.comm.rtcpdisconntimeout",
                              DEFAULT_SESSION_DISCONNECTED_TIMEOUT) * 1000;
        myDebugSessionDisconnectedTimeout =
            props.intProperty("conf.comm.rtcpdisconntimeout.debug",
                              DEFAULT_SESSION_DISCONNECTED_TIMEOUT) * 1000;
        mySessionBacklogLimit =
            props.intProperty("conf.comm.rtcpbacklog",
                              DEFAULT_SESSION_BACKLOG_LIMIT);
    }

    /**
     * Associate a particular TCP connection with an RTCP session.
     *
     * @param session  The session.
     * @param connection  The connection to associate with the session.
     */
    private void acquireTCPConnection(RTCPSessionConnection session,
                                      Connection connection)
    {
        mySessionsByConnection.put(connection, session);
        session.acquireTCPConnection(connection);
    }

    /**
     * Add a session to the session table.
     *
     * @param session  The session to add.
     */
    void addSession(RTCPSessionConnection session) {
        mySessions.put(session.sessionID(), session);
    }

    /**
     * Handle an RTCP 'ack' request, keeping the connection alive and updating
     * the server's picture of which messages the client has received.
     *
     * @param connection  The TCP connection upon which the 'ack' request was
     *    received.
     * @param clientRecvSeqNum  The client's received message sequence number
     */
    void doAck(Connection connection, int clientRecvSeqNum) {
        RTCPSessionConnection session = mySessionsByConnection.get(connection);
        if (session != null) {
            session.clientAck(clientRecvSeqNum);
        } else {
            String reply = makeErrorReply("noSession", null);
            sendWithLog(connection, reply);
        }
    }

    /**
     * Handle an RTCP 'end' request, causing the explicit termination of an
     * RTCP session by the client.
     *
     * @param connection  The TCP connection upon which the 'end' request
     *    was received.
     */
    void doEnd(Connection connection) {
        RTCPSessionConnection session = mySessionsByConnection.get(connection);
        if (session != null) {
            session.close();
        } else {
            trMsg.errorm("got RTCP end request on connection with no associated session " + connection);
        }
    }

    /**
     * Handle an RTCP 'error' request, which simply announces an error from the
     * client
     *
     * @param connection  The TCP connection upon which the 'error' request was
     *    received.
     * @param errorTag  The error tag string from the request
     * @param errorText  The error text from the request, or null if there
     *    wasn't any
     */
    void doError(Connection connection, String errorTag, String errorText) {
        if (trMsg.usage && Trace.ON) {
            String aux = "";
            if (errorText != null) {
                aux = " (" + errorText + ")";
            }
            trMsg.usagem(connection + " received error request " + errorTag +
                         aux);
        }
    }

    /**
     * Handle an RTCP message request, transmitting messages from the client
     * to the server.
     *
     * @param connection  The TCP connection upon which the message(s)
     *    was(were) delivered.
     * @param message   The RTCP request descriptor containing the message(s)
     *     sent from the client.
     */
    void doMessage(Connection connection, RTCPRequest message) {
        RTCPSessionConnection session = mySessionsByConnection.get(connection);
        if (session != null) {
            session.receiveMessage(message);
        } else {
            String reply = makeErrorReply("noSession", null);
            sendWithLog(connection, reply);
        }
    }

    /**
     * Handle an RTCP 'resume' request, causing the resumption of a session
     * previously interrupted by loss of its TCP connection.
     *
     * @param connection  The TCP connection upon which the 'resume' request
     *    was received.
     * @param sessionID  The session ID of the session whose resumption is to
     *    be attempted.
     */
    void doResume(Connection connection, String sessionID,
                  int clientRecvSeqNum)
    {
        RTCPSessionConnection session = mySessionsByConnection.get(connection);
        String reply = null;
        if (session != null) {
            reply = makeErrorReply("sessionInProgress", null);
        } else {
            session = getSession(sessionID);
            if (session != null) {
                acquireTCPConnection(session, connection);
                if (trMsg.event && Trace.ON) {
                    trMsg.eventm(session + " resume " + session.sessionID());
                }
                sendWithLog(connection,
                            makeResumeReply(session.sessionID(),
                                            session.clientSendSeqNum()));
                session.replayUnacknowledgedMessages(clientRecvSeqNum);
            } else {
                reply = makeErrorReply("noSuchSession", null);
            }
        }
        if (reply != null) {
            sendWithLog(connection, reply);
        }
    }

    /**
     * Handle an RTCP 'start' request, causing the creation of a new session.
     *
     * @param connection  The TCP connection upon which the 'start' request
     *    was received.
     */
    void doStart(Connection connection) {
        RTCPSessionConnection session = mySessionsByConnection.get(connection);
        String reply;
        if (session != null) {
            reply = makeErrorReply("sessionInProgress", null);
        } else {
            session = new RTCPSessionConnection(this);
            acquireTCPConnection(session, connection);
            if (trMsg.event && Trace.ON) {
                trMsg.eventm(session + " start " + session.sessionID());
            }
            reply = makeStartReply(session.sessionID());
        }
        sendWithLog(connection, reply);
    }

    /**
     * Look up the session associated with some session ID.
     *
     * @param sessionID  The ID of the session sought.
     *
     * @return the session whose session ID is 'sessionID', or null if there is
     *    no such session.
     */
    private RTCPSessionConnection getSession(String sessionID) {
        return mySessions.get(sessionID);
    }

    /**
     * Obtain the inner message handler factory for this factory.  This is the
     * factory for providing message handlers for the messages embedded inside
     * the RTCP requests whose handlers are in turn provided by this (outer)
     * factory.
     *
     * @return the inner message handler factory for this factory.
     */
    MessageHandlerFactory innerFactory() {
        return myInnerFactory;
    }

    /**
     * Generate the request line for an RTCP 'ack' request, acknowledging
     * client messages that have been received.
     *
     * @param clientSeqNum  The server's client message sequence number, i.e.,
     *    the serial number of the client message most recently received by
     *    the server.
     *
     * @return an RTCP 'ack' request line corresponding to the parameter.
     */
    String makeAck(int clientSeqNum) {
        return "ack " + clientSeqNum + "\n";
    }

    /**
     * Generate the request line for an RTCP 'error' request, typically the
     * error reply to some request received by the server.
     *
     * @param errorTag  The error tag string
     * @param explanataion  The human-readable explanation of the error, or
     *     null to omit.
     *
     * @return an RTCP 'error' request line corresponding to the parameters.
     */
    String makeErrorReply(String errorTag, String explanation) {
        String result = "error " + errorTag;
        if (explanation != null) {
            result += " " + explanation;
        }
        result += "\n";
        return result;
    }

    /**
     * Generate the request line for an RTCP message delivery request, sending
     * messages from the server to the client.
     *
     * @param serverSeqNum  The server's message sequence number, i.e., the
     *    serial number of the outgoing message.
     * @param clientSeqNum  The server's client message sequence number, i.e.,
     *    the serial number of the client message most recently received by
     *    the server.
     * @param message  The message itself, pre-encoded as a string (sans
     *    framing) by the caller.
     *
     * @return an RTCP message delivery request corresponding to the
     *    parameters.
     */
    String makeMessage(int serverSeqNum, int clientSeqNum, String message) {
        return serverSeqNum + " " + clientSeqNum + "\n" + message + "\n\n";
    }

    /**
     * Generate the request line for an RTCP 'resume' reply.
     *
     * @param sessionID  The session ID of the session being resumed
     * @param seqNum  The server's client message sequence number, i.e., the
     *    serial number of the client message most recently received by the
     *    server as part of the indicated session.
     *
     * @return an RTCP 'resume' reply line corresponding to the parameters.
     */
    String makeResumeReply(String sessionID, int seqNum) {
        return "resume " + sessionID + " " + seqNum + "\n";
    }

    /**
     * Generate the request line for an RTCP 'start' reply.
     *
     * @param sessionID  The session ID of the session being started.
     *
     * @return an RTCP 'start' reply line correponding to the parameter.
     */
    String makeStartReply(String sessionID) {
        return "start " + sessionID + "\n";
    }

    /**
     * Get the message trace object for this factory.  This trace object should
     * only be used for logging the content of message traffic.  Other server
     * events should be logged to Trace.comm.
     *
     * @return this framer's message trace object.
     */
    Trace msgTrace() {
        return trMsg;
    }

    /**
     * Obtain the network manager for this factory.
     *
     * @return this factory's network manager object.
     */
    NetworkManager networkManager() {
        return myManager;
    }
    
    /**
     * Provide a message handler for a new (RTCP over TCP) connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new RTCPMessageHandler(connection, this,
                                      sessionInactivityTimeout(false));
    }

    /**
     * Remove a session from the session table.
     *
     * @param session  The session to remove.
     */
    void removeSession(RTCPSessionConnection session) {
        mySessions.remove(session.sessionID());
    }

    /**
     * Send a message on a connection, with logging.
     *
     * @param connection  Connection to send on
     * @param msg  The message to send.
     */
    private void sendWithLog(Connection connection, String msg) {
        if (trMsg.debug && Trace.ON) {
            trMsg.debugm(connection + " <| " + msg.trim());
        }
        connection.sendMsg(msg);
    }

    /**
     * Obtain the RTCP session message backlog limit: the amount of outbound
     * message traffic we'll allow to accumulate before deeming the client
     * unmutual and killing the session.
     *
     * @param debug  If true, return the debug-mode limit; if false, return
     *    the normal use limit.
     *
     * @return the session backlog limit, in characters.
     */
    int sessionBacklogLimit(boolean debug) {
        return mySessionBacklogLimit;
    }

    /**
     * Obtain the RTCP session disconnected timeout interval: the time an RTCP
     * session can be without a live TCP connection before the server decides
     * that the user isn't coming back and kills the session.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     *    the normal use timeout.
     *
     * @return the session disconnected timeout interval, in milliseconds.
     */
    int sessionDisconnectedTimeout(boolean debug) {
        if (debug) {
            return myDebugSessionDisconnectedTimeout;
        } else {
            return mySessionDisconnectedTimeout;
        }
    }

    /**
     * Obtain the RTCP session inactivity timeout interval: the time an RTCP
     * session can be idle before the server kills it.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     *    the normal use timeout.
     *
     * @return the session inactivity timeout interval, in milliseconds.
     */
    int sessionInactivityTimeout(boolean debug) {
        if (debug) {
            return myDebugSessionInactivityTimeout;
        } else {
            return mySessionInactivityTimeout;
        }
    }

    /**
     * Receive notification that an underlying TCP connection has died.
     *
     * @param connection  The TCP connection that died.
     * @param reason  The reason why.
     */
    void tcpConnectionDied(Connection connection, Throwable reason) {
        RTCPSessionConnection session =
            mySessionsByConnection.remove(connection);
        
        if (session != null) {
            session.loseTCPConnection(connection);
            if (trMsg.event && Trace.ON) {
                trMsg.eventm(connection + " lost under " + session + "-" +
                                  session.sessionID() + ": " + reason);
            }
        } else {
            if (trMsg.event && Trace.ON) {
                trMsg.eventm(connection +
                             " lost under no known RTCP session: " + reason);
            }
        }
    }
}
