package org.elkoserver.foundation.net;

import org.elkoserver.util.trace.Trace;

/**
 * Byte I/O framer factory for JSON messaging over a byte stream.  The framing
 * rule used is: a block of one or more non-empty lines terminated by an empty
 * line (i.e., by two successive newlines).
 *
 * <p>On input, each block matching this framing rule is regarded as a
 * parseable unit; that is, it is expected to contain one or more syntactically
 * complete JSON messages.  The entire block is read into an internal buffer,
 * then parsed for JSON messages that are fed to the receiver.
 *
 * <p>On output, each message being sent is framed according to this rule.
 */
public class JSONByteIOFramerFactory implements ByteIOFramerFactory {
    private Trace trMsg;

    /**
     * Constructor.
     *
     * @param msgTrace  Trace object for logging message traffic.
     */
    public JSONByteIOFramerFactory(Trace msgTrace) {
        trMsg = msgTrace;
    }

    /**
     * Provide an I/O framer for a new connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label) {
        return new JSONByteIOFramer(trMsg, receiver, label);
    }
}
