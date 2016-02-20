package org.elkoserver.foundation.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An RTCP request descriptor, obtained by parsing the lines of text in an RTCP
 * request as they are received.
 */
public class RTCPRequest {
    /** State of request parsing */
    private int myParseState;

    private final static int STATE_AWAITING_VERB = 0;
    private final static int STATE_AWAITING_MESSAGE = 1;
    private final static int STATE_COMPLETE = 2;

    /** RTCP request verb */
    private int myVerb;

    public final static int VERB_START = 0;
    public final static int VERB_RESUME = 1;
    public final static int VERB_ACK = 2;
    public final static int VERB_MESSAGE = 3;
    public final static int VERB_END = 4;
    public final static int VERB_ERROR = 6;

    /* The following elements will be present or not according to whether the
       message indicated by the verb is supposed to container them. */

    /** Highest seq number of message from us that client claims receipt of. */
    private int myClientRecvSeqNum;

    /** Seq number of message bundle from client to us (message delivery). */
    private int myClientSendSeqNum;

    /** Session ID ("resume"). */
    private String mySessionID;

    /** Error tag ("error"). */
    private String myError;

    /** First message in a message bundle (message delivery). */
    private Object myMessage;

    /** Second and later messages in a message bundle, or null if there was
        only one (message delivery). */
    private LinkedList<Object> myOtherMessages;


    /** Regexp to match one or more spaces.  Compiled and cached here. */
    private final static Pattern theDelimiterPattern = Pattern.compile(" +");

    /**
     * Create a new RTCP request descriptor.  The descriptor still needs to be
     * filled in by parsing a request using parseRequestLine().
     */
    RTCPRequest() {
        myParseState = STATE_AWAITING_VERB;
    }

    /**
     * Add a message to the message bundle this descriptor holds.  This is for
     * use by the external entity responsible for parsing the remainder of a
     * message delivery after this class has parsed the request line.
     *
     * @param message  The message to be added.
     */
    void addMessage(Object message) {
        myParseState = STATE_COMPLETE;
        if (myMessage == null) {
            myMessage = message;
        } else {
            if (myOtherMessages == null) {
                myOtherMessages = new LinkedList<Object>();
            }
            myOtherMessages.addLast(message);
        }
    }

    /**
     * Obtain the client received sequence number, which is the highest message
     * sequence number of messages from us to the client that client claims to
     * have received successfully.
     *
     * This will be present in "resume", "ack", "end", and message delivery
     * requests.
     *
     * @return this request's client receive sequence number.
     */
    int clientRecvSeqNum() {
        return myClientRecvSeqNum;
    }

    /**
     * Obtain the client send sequence number, which is the sequence number of
     * message bundle carried by this message delivery request.
     *
     * @return this request's client send sequence number.
     */
    int clientSendSeqNum() {
        return myClientSendSeqNum;
    }

    /**
     * Obtain the error tag from this request.  This will be present in "error"
     * requests and as the consquence of parse failures.
     *
     * @return this request's error tag string.
     */
    String error() {
        return myError;
    }

    /**
     * Test if the request described by this object has been completely parsed
     * yet.  If not, the various request parameter values will not be valid.
     *
     * @return true iff request parsing is complete.
     */
    Boolean isComplete() {
        return myParseState == STATE_COMPLETE;
    }

    /**
     * Obtain the next message from the message bundle in this request.  This
     * method will provide one message each time it is called until the
     * messages in the request have been exhausted, after which it will always
     * return null.  Messages, of course, will only be present in message
     * delivery requests.
     *
     * @return the next available message in the this request, or null if all
     *    messages have been previously returned.
     */
    Object nextMessage() {
        Object result = myMessage;
        if (myOtherMessages != null) {
            myMessage = myOtherMessages.removeFirst();
            if (myOtherMessages.isEmpty()) {
                myOtherMessages = null;
            }
        } else {
            myMessage = null;
        }
        return result;
    }

    /**
     * Take note of an external parsing or I/O problem that prevents successful
     * completion of a valid RTCP request.
     *
     * @param problem  Exception describing what the issue was
     */
    void noteProblem(Exception problem) {
        myError = problem.getMessage();
        myParseState = STATE_COMPLETE;
        myVerb = VERB_ERROR;
    }

    /**
     * Parse an RTCP request line, extracting the verb, and parameters if any.
     *
     * Note that this only parses the request line.  In the case of a message
     * delivery, the portion of the request containing the message bundle
     * itself is processed externally.
     *
     * @param line  The line to be parsed.
     */
    void parseRequestLine(String line) {
        line = line.trim();
        String frags[] = theDelimiterPattern.split(line);
        String verb = frags[0];
        myParseState = STATE_COMPLETE;
        if (verb.equals("start")) {
            myVerb = VERB_START;
            if (frags.length != 1) {
                myVerb = VERB_ERROR;
                myError = "invalid start request";
            }
        } else if (verb.equals("resume")) {
            myVerb = VERB_RESUME;
            if (frags.length != 3) {
                myVerb = VERB_ERROR;
                myError = "invalid resume request";
            } else {
                mySessionID = frags[1];
                try {
                    myClientRecvSeqNum = Integer.parseInt(frags[2]);
                } catch (NumberFormatException e) {
                    myVerb = VERB_ERROR;
                    myError = "invalid resume request";
                }
            }
        } else if (verb.equals("ack")) {
            myVerb = VERB_ACK;
            if (frags.length != 2) {
                myVerb = VERB_ERROR;
                myError = "invalid resume request";
            } else {
                try {
                    myClientRecvSeqNum = Integer.parseInt(frags[1]);
                } catch (NumberFormatException e) {
                    myVerb = VERB_ERROR;
                    myError = "invalid ack request";
                }
            }
        } else if (verb.equals("end")) {
            myVerb = VERB_END;
            if (frags.length != 2) {
                myVerb = VERB_ERROR;
                myError = "invalid end request";
            } else {
                try {
                    myClientRecvSeqNum = Integer.parseInt(frags[1]);
                } catch (NumberFormatException e) {
                    myVerb = VERB_ERROR;
                    myError = "invalid end request";
                }
            }
        } else if (verb.equals("error")) {
            myVerb = VERB_ERROR;
            if (frags.length != 2) {
                myError = "invalid error request";
            } else {
                myError = "client reported error: " + frags[1];
            }
        } else {
            myVerb = VERB_MESSAGE;
            try {
                myClientSendSeqNum = Integer.parseInt(verb);
                if (frags.length != 2) {
                    myVerb = VERB_ERROR;
                    myError = "invalid message request";
                } else {
                    try {
                        myClientRecvSeqNum = Integer.parseInt(frags[1]);
                    } catch (NumberFormatException e) {
                        myVerb = VERB_ERROR;
                        myError = "invalid message request";
                    }
                }
            } catch (NumberFormatException e) {
                myVerb = VERB_ERROR;
                myError = "invalid RTCP verb " + verb;
            }
            if (myVerb == VERB_MESSAGE) {
                myParseState = STATE_AWAITING_MESSAGE;
            }
        }
    }

    /**
     * Obtain the session ID from this request.  This will be present only in
     * "resume" requests.
     *
     * @return this request's session ID.
     */
    String sessionID() {
        return mySessionID;
    }

    /**
     * Obtain this request's verb.  This will be one of the VERB_XXX
     * constants defined by this class.
     *
     * @return This requests' request verb code.
     */
    int verb() {
        return myVerb;
    }

    /**
     * Obtain a printable String representation of this request.
     *
     * @return a printable dump of the request state.
     */
    public String toString() {
        switch (myVerb) {
            case VERB_START:
                return "start";
            case VERB_RESUME:
                return "resume " + mySessionID + " " + myClientRecvSeqNum;
            case VERB_ACK:
                return "ack " + myClientRecvSeqNum;
            case VERB_MESSAGE:
                return "msg " + myClientSendSeqNum + " " + myClientRecvSeqNum;
            case VERB_END:
                return "end " + myClientRecvSeqNum;
            case VERB_ERROR:
                return "error " + myError;
            default:
                return "<unknown RTCP verb>";
        }
    }
}

