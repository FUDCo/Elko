package org.elkoserver.foundation.net;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.elkoserver.foundation.run.Queue;
import org.elkoserver.foundation.timer.Clock;
import org.elkoserver.foundation.timer.TickNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;

/**
 * An implementation of {@link Connection} that virtualizes a continuous
 * message session out of a series of transient HTTP connections.
 */
public class HTTPSessionConnection extends ConnectionBase
{
    /** Marker on send queue for select timeout. */
    static final private Object theTimeoutMarker = "(no messages)";

    /** Marker on send queue for connection close. */
    static final private Object theHTTPCloseMarker = "(end of session)";

    /** Trace object for logging message traffic. */
    private Trace trMsg;

    /** Server to client message sequence number. */
    private int mySelectSequenceNumber;

    /** Client to server message sequence number. */
    private int myXmitSequenceNumber;

    /** Queue of outgoing messages awaiting retrieval by the client. */
    private Queue myQueue;

    /** Flag indicating that connection is in the midst of shutting down. */
    private boolean amClosing;

    /** TCP connection for transmitting messages to the client, if a select is
        currently pending, or null if not. */
    private Connection myDownstreamConnection;

    /** Flag that downstream connection, if there is one, is non-persistent. */
    private boolean myDownstreamIsNonPersistent;

    /** The factory that created this session. */
    private HTTPMessageHandlerFactory mySessionFactory;

    /** HTTP framer to interpret HTTP POSTs and format HTTP replies. */
    private HTTPFramer myHTTPFramer;

    /** Clock: ticks watch for expired message selects. */
    private Clock mySelectClock;

    /** Clock: ticks watch for dead session. */
    private Clock myInactivityClock;

    /** Time that connection started waiting for a message select to be
        responded to, or 0 if it isn't waiting for that. */
    private long mySelectWaitStartTime;

    /** Last time that there was any traffic on this connection from the user,
        to enable detection of inactive sessions. */
    private long myLastActivityTime;

    /** Time a select request may sit waiting without sending a response, in
        milliseconds. */
    private int mySelectTimeoutInterval;

    /** Time a session may sit idle before killing it, in milliseconds. */
    private int mySessionTimeoutInterval;

    /** Open TCP connections associated with this session, for cleanup. */
    private Set<Connection> myConnections;

    /** Session ID -- a swiss number to authenticate client HTTP requests. */
    private long mySessionID;

    /** Random number generator, for creating session IDs. */
    static private SecureRandom theRandom = new SecureRandom();

    /** Flag to bypass unguessable ID generation for debugging purposes. */
    static public boolean TheDebugSessionsFlag = false;


    /**
     * Make a new HTTP session connection object for an incoming connection,
     * with a new, internally generated, session ID.
     *
     * @param sessionFactory  Factory for creating HTTP message handler objects
     */
    HTTPSessionConnection(HTTPMessageHandlerFactory sessionFactory) {
        this(sessionFactory, Math.abs(theRandom.nextLong()));
    }

    /**
     * Make a new HTTP session connection object for an incoming connection,
     * with a given session ID.
     *
     * @param sessionFactory  Factory for creating HTTP message handler objects
     * @param sessionID  The session ID for the session.
     */
    HTTPSessionConnection(HTTPMessageHandlerFactory sessionFactory,
                          long sessionID)
    {
        super(sessionFactory.networkManager());
        mySessionFactory = sessionFactory;
        trMsg = mySessionFactory.httpFramer().msgTrace();
        mySessionID = sessionID;
        mySessionFactory.addSession(this);
        myConnections = new HashSet<Connection>();
        myLastActivityTime = System.currentTimeMillis();
        if (trMsg.event && Trace.ON) {
            trMsg.eventi(this + " new connection");
        }
        mySelectSequenceNumber = 1;
        myXmitSequenceNumber = 1;
        clearDownstreamConnection();
        myQueue = new Queue();
        myHTTPFramer = mySessionFactory.httpFramer();
        amClosing = false;

        mySelectTimeoutInterval = mySessionFactory.selectTimeout(false);
        mySelectClock =
            Timer.theTimer().every((mySelectTimeoutInterval + 1000) / 4,
                                   new TickNoticer() {
                                       public void noticeTick(int ignored) {
                                           noticeSelectTick();
                                       }
                                   });
        mySelectClock.start();

        mySessionTimeoutInterval = mySessionFactory.sessionTimeout(false);
        myInactivityClock =
            Timer.theTimer().every(mySessionTimeoutInterval + 1000,
                                   new TickNoticer() {
                                       public void noticeTick(int ignored) {
                                           noticeInactivityTick();
                                       }
                                   });
        myInactivityClock.start();

        enqueueHandlerFactory(mySessionFactory.innerFactory());
    }

    /**
     * Associate a TCP connection with this session.
     *
     * @param connection  The TCP connection used.
     */
    void associateTCPConnection(Connection connection) {
        myConnections.add(connection);
        if (trMsg.debug && Trace.ON) {
            trMsg.debugm("associate " + connection + " with " + this +
                         ", count=" + myConnections.size());
        }
    }

    /**
     * Set this session's downstream connection to null, so that it now has no
     * downstream connection.
     */
    private void clearDownstreamConnection() {
        myDownstreamConnection = null;
        myDownstreamIsNonPersistent = false;
        mySelectWaitStartTime = 0;
    }

    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    public void close() {
        if (!amClosing) {
            amClosing = true;

            Set<Connection> killConnections = myConnections;
            myConnections = new HashSet<Connection>();
            for (Connection connection : killConnections) {
                if (connection != myDownstreamConnection) {
                    connection.close();
                }
            }

            mySessionFactory.removeSession(this);
            myInactivityClock.stop();
            mySelectClock.stop();
            mySelectWaitStartTime = 0;
            if (myDownstreamConnection != null) {
                myDownstreamIsNonPersistent = true;
                sendMsg(theHTTPCloseMarker);
            }
            connectionDied(
                new ConnectionCloseException("Normal HTTP session close"));
        }
    }

    /**
     * Handle loss of an underlying TCP connection.
     *
     * @param connection  The TCP connection that died.
     */
    void dissociateTCPConnection(Connection connection) {
        myConnections.remove(connection);
        tcpConnectionDied(connection);
    }

    /**
     * Test if this session has any outbound messages that haven't yet been
     * sent.
     *
     * @return true if there are pending messages to send, false if not
     */
    boolean hasOutboundMessages() {
        return myQueue.hasMoreElements();
    }

    /**
     * Force initialization of the secure random number generator.
     *
     * This is a kludge motivated by said initialization being very slow.
     * Ideally, any long initialization delay ought to happen at system startup
     * time, before anybody is using the system who would care.  However,
     * Java's random number generator uses lazy initialization and won't
     * actually initialize itself until the first time it is used.  In ordinary
     * use, that would be the first time somebody tried to connect.  Users
     * shouldn't be subjected to random, mysterious long delays, so generating
     * one gratuitous random number here forces the initialization cost to be
     * paid at startup time as was desired.
     */
    static void initializeRNG() {
        /* Get the initialization delay over right now */
        long junk = theRandom.nextLong();
    }

    /**
     * Test if this session currently has a pending select waiting.
     *
     * @return true if this session has an open select request, false if not.
     */
    boolean isSelectWaiting() {
        return myDownstreamConnection != null;
    }

    /**
     * Take notice that the client session is still active.
     */
    void noteClientActivity() {
        if (!amClosing) {
            myLastActivityTime = System.currentTimeMillis();
        }
    }

    /**
     * React to a clock tick event on the select timeout timer.
     *
     * If there is a pending message select, check to see if the client has
     * waited too long; if so, send an empty reply to the select.
     */
    private void noticeSelectTick() {
        if (mySelectWaitStartTime != 0) {
            long now = System.currentTimeMillis();
            if (now - mySelectWaitStartTime > mySelectTimeoutInterval) {
                mySelectWaitStartTime = 0;
                noteClientActivity();
                sendMsg(theTimeoutMarker);
            }
        }
    }

    /**
     * React to a clock tick event on the inactivity timeout timer.
     *
     * Check to see if it has been too long since anything was received from
     * the client; if so, kill the session.
     */
    private void noticeInactivityTick() {
        if (mySelectWaitStartTime == 0 &&
                System.currentTimeMillis() - myLastActivityTime >
                    mySessionTimeoutInterval) {
            if (trMsg.event && Trace.ON) {
                trMsg.eventm(this + " tick: HTTP session timeout");
            }
            close();
        } else {
            if (trMsg.debug && Trace.ON) {
                trMsg.debugm(this + " tick: HTTP session waiting");
            }
        }
    }

    /**
     * Wrap a message in the appropriate HTML.
     *
     * @param message  The message itself.
     * @param start  true if this message is the first in a batch.
     * @param end  true if this message is the last in a batch.
     *
     * @return a string containing the appropriate HTML wrapped around
     *    'message'.
     */
    private String packMessage(Object message, boolean start, boolean end) {
        if (start) {
            ++mySelectSequenceNumber;
        }
        int sequenceNumber = mySelectSequenceNumber;
        if (message == theTimeoutMarker) {
            message = null;
        } else if (message == theHTTPCloseMarker) {
            message = null;
            sequenceNumber = -1;
        }
        return myHTTPFramer.makeSelectReplySegment(message, sequenceNumber,
                                                   start, end);
    }

    /**
     * Handle an /xmit/ request from the client, delivering one or more
     * messages.  The message(s) is (are) placed on the run queue and an
     * appropriate acknowledgement HTTP reply is sent back to the sender.
     *
     * @param connection  The TCP connection the messages were delivered on.
     * @param uri  The components of the requested /xmit/ HTTP URI.
     * @param message  The message body that was delivered.
     */
    void receiveMessage(Connection connection, SessionURI uri, String message){
        if (amClosing) {
            connection.close();
        } else {
            noteClientActivity();
            if (trMsg.event && Trace.ON) {
                trMsg.msgi(this + ":" + connection, true, message);
            }
            String reply;
            if (uri.sequenceNumber != myXmitSequenceNumber) {
                trMsg.errorm(this + " expected xmit seq # " +
                             myXmitSequenceNumber + ", got " +
                             uri.sequenceNumber);
                reply = myHTTPFramer.makeSequenceErrorReply("sequenceError");
            } else {
                Iterator unpacker = myHTTPFramer.postBodyUnpacker(message);
                while (unpacker.hasNext()) {
                    enqueueReceivedMessage(unpacker.next());
                }
                ++myXmitSequenceNumber;
                reply = myHTTPFramer.makeXmitReply(myXmitSequenceNumber);
            }
            connection.sendMsg(reply);
        }
    }

    /**
     * Handle a /select/ request from the client, polling for message traffic.
     * If there are any outgoing messages pending in the queue, send them
     * immediately in an HTTP reply.  Otherwise, leave the request open until
     * there is something to send.
     *
     * @param downstreamConnection  TCP connection for delivering messages.
     * @param uri  The components of the requested /select/ HTTP URI.
     * @param nonPersistent  True if HTTP request was flagged non-persistent.
     *
     * @return true if an HTTP reply was sent.
     */
    boolean selectMessages(Connection downstreamConnection, SessionURI uri,
                           boolean nonPersistent)
    {
        noteClientActivity();

        if (uri.sequenceNumber != mySelectSequenceNumber) {
            /* Client did a bad, bad thing. */
            trMsg.errorm(this + " expected select seq # " +
                         mySelectSequenceNumber + ", got " +
                         uri.sequenceNumber);
            downstreamConnection.sendMsg(
                myHTTPFramer.makeSequenceErrorReply("sequenceError"));
            return true;

        } else if (myQueue.hasMoreElements()) {
            /* There are messages waiting, so send them. */
            String reply = "";
            boolean start = true;
            boolean end;
            do {
                Object message = myQueue.nextElement();
                end = !myQueue.hasMoreElements();
                if (trMsg.event && Trace.ON) {
                    trMsg.msgi(this + ":" + downstreamConnection, false,
                               message);
                }
                reply += packMessage(message, start, end);
                start = false;
            } while (!end);
            downstreamConnection.sendMsg(reply);
            clearDownstreamConnection();
            return true;

        } else if (amClosing) {
            /* Session connection is in the midst of closing, so drop the TCP
               connection. */
            downstreamConnection.close();
            return false;

        } else {
            /* Nothing to do yet, so block awaiting outbound traffic. */
            myDownstreamConnection = downstreamConnection;
            myDownstreamIsNonPersistent = nonPersistent;
            mySelectWaitStartTime = System.currentTimeMillis();
            return false;
        }
    }

    /**
     * Send a message to the other end of the connection.
     *
     * @param message  The message to be sent.
     */
    public void sendMsg(Object message) {
        if (myDownstreamConnection != null) {
            /* If there *is* a pending select request, use it to send the
               message immediately. */
            if (trMsg.event && Trace.ON) {
                trMsg.msgi(this + ":" + myDownstreamConnection, false,
                           message);
            }
            myDownstreamConnection.sendMsg(packMessage(message, true, true));
            if (myDownstreamIsNonPersistent) {
                Connection toClose = myDownstreamConnection;
                clearDownstreamConnection();
                toClose.close();
            } else {
                clearDownstreamConnection();
            }
            noteClientActivity();

        } else {
            /* If there *is not* a pending select request, put the message on
               the outgoing message queue. */
            myQueue.enqueue(message);
        }
    }

    /**
     * Get this session's session factory.
     *
     * @return the HTTP session factory object for this session.
     */
    HTTPMessageHandlerFactory sessionFactory() {
        return mySessionFactory;
    }

    /**
     * Get this session's ID number.
     *
     * @return the session ID number of this session.
     */
    long sessionID() {
        return mySessionID;
    }

    /**
     * Turn debug features for this connection on or off. In the case of an
     * HTTP session, debug mode involves using longer timeouts so that things
     * work on a human time scale when debugging.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    public void setDebugMode(boolean mode) {
        mySelectTimeoutInterval = mySessionFactory.selectTimeout(mode);
        mySessionTimeoutInterval = mySessionFactory.sessionTimeout(mode);
    }

    /**
     * Handle loss of an underlying TCP connection.  This happens all the time
     * in the HTTP world, and is only a (mild) problem if there was a pending
     * select request open on the connection.
     *
     * @param connection  The TCP connection that died.
     *
     * @return true if this session was using 'connection'.
     */
    boolean tcpConnectionDied(Connection connection) {
        if (myDownstreamConnection == connection) {
            clearDownstreamConnection();
            noteClientActivity();
            if (trMsg.event && Trace.ON) {
                trMsg.eventm(this + " lost " + connection +
                             " with pending select");
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a printable String representation of this connection.
     *
     * @return a printable representation of this connection.
     */
    public String toString() {
        return "HTTP(" + id() + "," + mySessionID + ")";
    }
}

