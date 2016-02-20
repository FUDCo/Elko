package org.elkoserver.foundation.net;

/**
 * Interface supporting protocol-specific message framing on a connection,
 * to frame inchoate streams of bytes into processable units.
 */
public interface ByteIOFramerFactory {
    /**
     * Provide an I/O framer for a new connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label);
}

