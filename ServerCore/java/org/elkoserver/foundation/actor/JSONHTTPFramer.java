package org.elkoserver.foundation.actor;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import org.elkoserver.foundation.net.HTTPFramer;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Parser;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.util.trace.Trace;

/**
 * HTTP message framer for JSON messages transported via HTTP.
 *
 * <p>This class treats the content of each HTTP POST to the /xmit/ URL as a
 * bundle of one or more JSON messages to be handled.
 */
public class JSONHTTPFramer extends HTTPFramer {
    /**
     * Constructor.
     */
    public JSONHTTPFramer(Trace appTrace) {
        super(appTrace);
    }

    /**
     * Produce the HTTP for responding to an HTTP GET of the /select/ URL by
     * sending a message to the client.
     *
     * <p>The actual HTTP reply body sent is constructed by concatenating the
     * results of one or more coordinated calls to this method, one call for
     * each message that is to be sent.  In the first of these calls, the
     * 'start' flag must be true.  In the last, the 'end' flag must be true.
     * (If only one message is being sent, this method should be called exactly
     * once with both 'start' and 'end' set to true.)
     *
     * @param message  The message to be sent.
     * @param seqNumber  The sequence number for the next select request.
     * @param start  true if this message is the first in a batch of messages.
     * @param end  true if this message is the last in a batch of messages.
     *
     * @return an appropriate HTTP reply body string for responding to a
     *    /select/ GET, delivering 'message' to the client.
     */
    public String makeSelectReplySegment(Object message, int seqNumber,
                                         boolean start, boolean end)
    {
        String messageString;
        if (message instanceof JSONLiteral) {
            messageString = ((JSONLiteral) message).sendableString();
        } else if (message instanceof JSONObject) {
            messageString = ((JSONObject) message).sendableString();
        } else if (message instanceof String) {
            messageString = "\"" + ((String) message) + "\"";
        } else {
            messageString = null;
        }
        return super.makeSelectReplySegment(messageString, seqNumber,
                                            start, end);
    }

    /**
     * Get an iterator that can extract the JSON message or messages (if any)
     * from the body of an HTTP message.
     *
     * @param body  The HTTP message body in question.
     *
     * @return an iterator that can be called upon to return the JSON
     *    message(s) contained within 'body'.
     */
    public Iterator postBodyUnpacker(String body) {
        return new JSONBodyUnpacker(body);
    }

    /**
     * Post body unpacker for a bundle of JSON messages.  In this case, the
     * HTTP POST body contains one or more JSON messages.
     */
    private class JSONBodyUnpacker implements Iterator {
        /** Current JSON parser. */
        private Parser myParser;

        /** Last JSON message parsed.  This will be the next JSON message to be
            returned because the framer always parses one JSON message ahead,
            in order to be able to see the end of the HTTP message string. */
        private Object myLastMessageParsed;

        /**
         * Constructor. Strip the form variable name and parse the rest as
         * JSON.
         *
         * @param postBody  The HTTP message body was POSTed.
         */
        public JSONBodyUnpacker(String postBody) {
            Trace.comm.debugm("unpacker for: /" + postBody + "/");
            int junkMark = postBody.indexOf('=');
            if (junkMark >= 0) {

                // XXX Temporary hack to decode POSTs from Safari
                if (postBody.length() > junkMark &&
                        postBody.charAt(junkMark+1) == '%') {
                    try {
                        postBody = URLDecoder.decode(postBody, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        /* This should never happen. */
                        Trace.comm.errorm("bogus " + e);
                    }
                }

                int startOfMessageMark = postBody.indexOf('{');
                if (startOfMessageMark > junkMark) {
                    postBody = postBody.substring(junkMark + 1);
                }
            }
            myParser = new Parser(postBody);
            myLastMessageParsed = parseNextMessage();
        }

        /**
         * Test if there are more messages.
         *
         * Since the framer always parse one message ahead, just look to see if
         * there is a message sitting there.
         */
        public boolean hasNext() {
            return myLastMessageParsed != null;
        }

        /**
         * Get the next message.
         *
         * Return the last message parsed, then parse the next message for the
         * next time this is called (the parser will return null after it has
         * parsed the last actual messge).
         */
        public Object next() {
            Object result = myLastMessageParsed;
            myLastMessageParsed = parseNextMessage();
            return result;
        }

        /**
         * Helper routine to actually do the message parsing.
         */
        private Object parseNextMessage() {
            try {
                return myParser.parseObjectLiteral();
            } catch (SyntaxError e) {
                if (NetworkManager.TheDebugReplyFlag) {
                    return e;
                }
                if (Trace.comm.warning && Trace.ON) {
                    Trace.comm.warningm("syntax error in JSON message: " +
                                        e.getMessage());
                }
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
