package org.elkoserver.foundation.net;

import java.util.HashMap;
import java.util.Map;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.util.trace.Trace;

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a series of HTTP requests.
 *
 * The challenge is that HTTP can't be relied on to hold open a single TCP
 * connection continuously, even though that's the desired abstraction.
 * Instead, a series of pontentially short-lived HTTP over TCP connections need
 * to be turned into a single seamless message stream.  The correlation between
 * HTTP requests and their associated message connections is done via swiss
 * numbers in the URLs.  That job is done by HTTPMessageHandler objects, which
 * are dispensed here, and their associated HTTPSessionConnection objects.
 */
class HTTPMessageHandlerFactory implements MessageHandlerFactory {
    /** The message handler factory for the messages embedded in the composite
        stream. */
    private MessageHandlerFactory myInnerFactory;

    /** HTTP framer to interpret HTTP POSTs and format HTTP replies. */
    private HTTPFramer myHTTPFramer;

    /** The root URI for GETs and POSTs. */
    private String myRootURI;

    /** Table of current sessions, indexed by ID number. */
    private Map<Long, HTTPSessionConnection> mySessions;

    /** Table of current sessions, indexed by TCP connection. */
    private Map<Connection, HTTPSessionConnection> mySessionsByConnection;

    /** Network manager for this server */
    private NetworkManager myManager;

    /** Time an HTTP select request can wait before it must be responded to, in
        milliseconds. */
    private int mySelectTimeout;

    /** Like mySelectTimeout, but when connection is in debug mode. */
    private int myDebugSelectTimeout;

    /** Default select timeout if none is explicitly given, in seconds. */
    static final int DEFAULT_SELECT_TIMEOUT = 60;

    /** Time an HTTP session can sit idle before being killed, in
        milliseconds. */
    private int mySessionTimeout;

    /** Like mySessionTimeout, but when connection is in debug mode. */
    private int myDebugSessionTimeout;

    /** Default session timeout if none is explicitly given, in seconds. */
    static final int DEFAULT_SESSION_TIMEOUT = 15;

    /**
     * Each HTTP message handler wraps an application-level message handler,
     * which is the entity that will actually process the messages extracted
     * from the HTTP requests, so the HTTP message handler factory needs to
     * wrap the application-level message handler factory.
     *
     * @param innerFactory  The application-level message handler factor that
     *   is to be wrapped by this.
     * @param rootURI  The root URI for GETs and POSTs.
     * @param httpFramer  HTTP framer to interpret HTTP POSTs and format HTTP
     *    replies.
     * @param manager  Network manager for this server.
     */
    HTTPMessageHandlerFactory(MessageHandlerFactory innerFactory,
                              String rootURI, HTTPFramer httpFramer,
                              NetworkManager manager)
    {
        myInnerFactory = innerFactory;
        myRootURI = "/" + rootURI + "/";
        mySessions = new HashMap<Long, HTTPSessionConnection>();
        mySessionsByConnection =
            new HashMap<Connection, HTTPSessionConnection>();
        myHTTPFramer = httpFramer;
        myManager = manager;

        BootProperties props = manager.props();

        mySelectTimeout =
            props.intProperty("conf.comm.httpselectwait",
                              DEFAULT_SELECT_TIMEOUT) * 1000;
        myDebugSelectTimeout =
            props.intProperty("conf.comm.httpselectwait.debug",
                              DEFAULT_SELECT_TIMEOUT) * 1000;

        mySessionTimeout =
            props.intProperty("conf.comm.httptimeout",
                              DEFAULT_SESSION_TIMEOUT) * 1000;
        myDebugSessionTimeout =
            props.intProperty("conf.comm.httptimeout.debug",
                              DEFAULT_SESSION_TIMEOUT) * 1000;
    }

    /**
     * Add a session to the session table.
     *
     * @param session  The session to add.
     */
    void addSession(HTTPSessionConnection session) {
        mySessions.put(session.sessionID(), session);
    }

    /**
     * Record the association of a TCP connection with an HTTP session.
     *
     * @param session  The session.
     * @param connection  The connection to associate with the session.
     */
    private void associateTCPConnection(HTTPSessionConnection session,
                                        Connection connection)
    {
        HTTPSessionConnection knownSession =
            mySessionsByConnection.get(connection);
        if (knownSession != null) {
            knownSession.dissociateTCPConnection(connection);
        }
        mySessionsByConnection.put(connection, session);
        session.associateTCPConnection(connection);
    }

    /**
     * Handle an HTTP GET of a /connect/ URI, causing the creation of a new
     * session.
     *
     * @param connection  The TCP connection upon which the connection request
     *    was received.
     * @param uri  HTTP GET URI fields.
     *
     * @return true if an HTTP reply was sent.
     */
    private boolean doConnect(Connection connection, SessionURI uri) {
        HTTPSessionConnection session = new HTTPSessionConnection(this);

        associateTCPConnection(session, connection);
        if (Trace.comm.event && Trace.ON) {
            Trace.comm.eventm(session + " connect over " + connection);
        }
        String reply = myHTTPFramer.makeConnectReply(session.sessionID());

        connection.sendMsg(reply);
        return true;
    }

    /**
     * Handle an HTTP GET of a /disconnect/ URI, causing the explicit
     * termination of an HTTP session by the browser.
     *
     * @param connection  The TCP connection upon which the disconnect request
     *    was received.
     * @param uri  HTTP GET URI fields.
     *
     * @return true if an HTTP reply was sent.
     */
    private boolean doDisconnect(Connection connection, SessionURI uri) {
        HTTPSessionConnection session = lookupSessionFromURI(connection, uri);
        if (session != null) {
            associateTCPConnection(session, connection);
            session.noteClientActivity();
        }

        String reply;
        if (session == null) {
            Trace.comm.errorm("got disconnect with invalid session " +
                              uri.sessionID);
            reply = myHTTPFramer.makeSequenceErrorReply("sessionIDError");
        } else {
            reply = myHTTPFramer.makeDisconnectReply();
        }
        connection.sendMsg(reply);
        if (session != null) {
            session.close();
        }
        return true;
    }

    /**
     * Handle an HTTP GET or POST of a bad URI.
     *
     * @param connection  The TCP connection upon which the bad URI
     *    request was received.
     * @param uri  The bad URI that was requested.
     *
     * @return true if an HTTP reply was sent.
     */
    private boolean doError(Connection connection, String uri) {
        if (Trace.comm.usage && Trace.ON) {
            Trace.comm.usagem(connection +
                              " received invalid URI in HTTP request " + uri);
        }
        connection.sendMsg(new HTTPError(404, "Not Found",
                                         myHTTPFramer.makeBadURLReply(uri)));
        return true;
    }

    /**
     * Handle an HTTP GET of a /select/ URI, requesting the delivery of
     * messages from the server to the client.
     *
     * @param connection  The TCP connection upon which the select request was
     *    received.
     * @param uri  HTTP GET URI fields.
     * @param nonPersistent  True if this request was flagged non-persistent.
     *
     * @return true if an HTTP reply was sent.
     */
    private boolean doSelect(Connection connection, SessionURI uri,
                             boolean nonPersistent)
    {
        HTTPSessionConnection session = lookupSessionFromURI(connection, uri);
        if (session != null) {
            associateTCPConnection(session, connection);
            return session.selectMessages(connection, uri, nonPersistent);
        } else {
            Trace.comm.errorm("got select with invalid session " +
                              uri.sessionID);
            connection.sendMsg(
                myHTTPFramer.makeSequenceErrorReply("sessionIDError"));
            return true;
        }
    }

    /**
     * Handle an HTTP GET or POST of an /xmit/ URI, transmitting messages from
     * the client to the server.
     *
     * @param connection  The TCP connection upon which the message(s)
     *    was(were) delivered.
     * @param uri  HTTP GET or POST URI fields.
     * @param message   The body of the message(s) sent from the client.
     *
     * @return true if an HTTP reply was sent.
     */
    private boolean doXmit(Connection connection, SessionURI uri,
                           String message)
    {
        HTTPSessionConnection session = lookupSessionFromURI(connection, uri);
        if (session != null) {
            associateTCPConnection(session, connection);
            session.receiveMessage(connection, uri, message);
        } else {
            Trace.comm.errorm("got xmit with invalid session " +
                              uri.sessionID);
            connection.sendMsg(
                myHTTPFramer.makeSequenceErrorReply("sessionIDError"));
        }
        return true;
    }

    /**
     * Look up the session associated with some session ID.
     *
     * @param sessionID  The ID number of the session sought.
     *
     * @return the session whose session ID is 'sessionID', or null if there is
     *    no such session.
     */
    private HTTPSessionConnection getSession(long sessionID) {
        return mySessions.get(sessionID);
    }

    /**
     * Process an HTTP GET request, which (depending on the URI) may be
     * variously a request to connect a new session, to poll for server to
     * client messages for a session, a delivery of client to server messages
     * for a session, or to a request to disconnect a session.
     *
     * @param connection  The TCP connection on which the HTTP request was
     *    received.
     * @param uri  The URI that was requested.
     * @param nonPersistent  True if this request was flagged non-persistent.
     */
    void handleGET(Connection connection, String uri, boolean nonPersistent) {
        SessionURI parsed = new SessionURI(uri, myRootURI);
        boolean replied;
        if (!parsed.valid) {
            replied = doError(connection, uri);
        } else if (parsed.verb == SessionURI.VERB_CONNECT) {
            replied = doConnect(connection, parsed);
        } else if (parsed.verb == SessionURI.VERB_SELECT) {
            replied = doSelect(connection, parsed, nonPersistent);
        } else if (parsed.verb == SessionURI.VERB_DISCONNECT) {
            replied = doDisconnect(connection, parsed);
        } else {
            replied = doError(connection, uri);
        }
        if (replied && nonPersistent) {
            connection.close();
        }
    }

    /**
     * Process an HTTP OPTIONS request, used in the braindamaged, useless, but
     * seemingly inescapable request preflight handshake required by the CORS
     * standard for cross site request handling.
     *
     * @param connection  The TCP connection on which the HTTP request was
     *    received.
     * @param request  The HTTP request itself, from which we will extract
     *    header information.
     */
    void handleOPTIONS(Connection connection, HTTPRequest request) {
        if (Trace.comm.event && Trace.ON) {
            Trace.comm.eventm("OPTIONS request over " + connection);
        }
        HTTPOptionsReply reply = new HTTPOptionsReply(request);
        connection.sendMsg(reply);
    }

    /**
     * Process an HTTP POST request, delivering messages for a session.
     *
     * @param connection   The TCP connection on which the HTTP request was
     *    received.
     * @param uri  The URI that was posted.
     * @param nonPersistent  True if this request was flagged non-persistent.
     * @param message  The message body.
     */
    void handlePOST(Connection connection, String uri, boolean nonPersistent,
                    String message)
    {
        SessionURI parsed = new SessionURI(uri, myRootURI);
        
        boolean replied;
        if (!parsed.valid) {
            replied = doError(connection, uri);
        } else if (parsed.verb == SessionURI.VERB_SELECT) {
            replied = doSelect(connection, parsed, nonPersistent);
        } else if (parsed.verb == SessionURI.VERB_XMIT_POST) {
            replied = doXmit(connection, parsed, message);
        } else if (parsed.verb == SessionURI.VERB_CONNECT) {
            replied = doConnect(connection, parsed);
        } else if (parsed.verb == SessionURI.VERB_DISCONNECT) {
            replied = doDisconnect(connection, parsed);
        } else {
            replied = doError(connection, uri);
        }
        if (replied && nonPersistent) {
            connection.close();
        }
    }

    /**
     * Get the HTTP framer for this factory.
     *
     * @return this factory's HTTP framer object.
     */
    HTTPFramer httpFramer() {
        return myHTTPFramer;
    }

    /**
     * Obtain the inner message handler factory for this factory.  This is the
     * factory for providing message handlers for the messages embedded inside
     * the HTTP requests whose handlers are in turn provided by this (outer)
     * factory.
     *
     * @return the inner message handler factory for this factory.
     */
    MessageHandlerFactory innerFactory() {
        return myInnerFactory;
    }

    /**
     * Determine the HTTP session object associated with a requested URI.
     *
     * @param connection  The connection that referenced the URI.
     * @param uri  The URI uri describing the session of interest.
     *
     * @return the HTTP session corresponding to the session ID in 'uri', or
     *    null if there was no such session.
     */
    private HTTPSessionConnection lookupSessionFromURI(Connection connection,
                                                       SessionURI uri)
    {
        HTTPSessionConnection session = getSession(uri.sessionID);
        if (session != null) {
            return session;
        }
        if (Trace.comm.usage && Trace.ON) {
            Trace.comm.usagem(connection + " received invalid session ID " +
                              uri.sessionID);
        }
        return null;
    }

    /**
     * Get the network manager for this factory.
     *
     * @return this factory's network manager object.
     */
    NetworkManager networkManager() {
        return myManager;
    }
    
    /**
     * Provide a message handler for a new (HTTP over TCP) connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new HTTPMessageHandler(connection, this, sessionTimeout(false));
    }

    /**
     * Remove a session from the session table.
     *
     * @param session  The session to remove.
     */
    void removeSession(HTTPSessionConnection session) {
        mySessions.remove(session.sessionID());
    }

    /**
     * Get the HTTP select timeout interval: the time an HTTP request for a
     * select URL can remain open with no message traffic before the server
     * must respond.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     *    the normal use timeout.
     *
     * @return the select timeout interval, in milliseconds.
     */
    int selectTimeout(boolean debug) {
        if (debug) {
            return myDebugSelectTimeout;
        } else {
            return mySelectTimeout;
        }
    }

    /**
     * Get the HTTP session timeout interval: the time an HTTP session can be
     * idle before the server kills it.
     *
     * @param debug  If true, return the debug-mode timeout; if false, return
     *    the normal use timeout.
     *
     * @return the session timeout interval, in milliseconds.
     */
    int sessionTimeout(boolean debug) {
        if (debug) {
            return myDebugSessionTimeout;
        } else {
            return mySessionTimeout;
        }
    }

    /**
     * Receive notification that an underlying TCP connection has died.
     *
     * @param connection  The TCP connection that died.
     * @param reason  The reason why.
     */
    void tcpConnectionDied(Connection connection, Throwable reason) {
        HTTPSessionConnection session =
            mySessionsByConnection.remove(connection);
        
        if (session != null) {
            session.dissociateTCPConnection(connection);
            if (Trace.comm.event && Trace.ON) {
                Trace.comm.eventm(connection + " lost under " + session +
                                  ": " + reason);
            }
        } else {
            if (Trace.comm.event && Trace.ON) {
                Trace.comm.eventm(connection +
                                  " lost under no known HTTP session: " +
                                  reason);
            }
        }
    }
}
