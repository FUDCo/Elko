package org.elkoserver.foundation.net;

import java.io.IOException;

/**
 * Interface supporting message framing services.
 *
 * <p>A ByteIOFramer is responsible for actually extracting messages from the
 * bytes arriving over a connection and doing something meaningful with them.
 * It is also responsible for actually producing the bytes to be transmitted
 * when a message is sent over a connection
 */
public interface ByteIOFramer {
    /**
     * Process bytes of data received.
     *
     * @param data  The bytes received.  The caller is not obligated to
     *    maintain the contents of this array after this method returns.
     * @param length  Number of usable bytes in 'data' (which may be different
     *    from the length of the 'data' array itself).  The end of input is
     *    indicated by a 'length' value of 0.
     */
    public void receiveBytes(byte[] data, int length) throws IOException;

    /**
     * Produce the bytes for writing a message to a connection.
     *
     * <p>Although the message is declared to be of class Object, particular
     * implementors of this interface may accept a more limited set of classes
     * as valid message types.  It is up to the particular implementor to
     * document which types it actually accepts.  It is up to the user of a
     * particular implementation to limit its message transmissions to the
     * prescribed set of types.
     *
     * @param message  The message to be written.
     *
     * @return a byte array containing the writable form of 'message'.
     */
    public byte[] produceBytes(Object message) throws IOException;
}

