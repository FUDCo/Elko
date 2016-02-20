package org.elkoserver.foundation.net;

/**
 * A communications connection to other entities on the net.
 */
public interface Connection {
    /**
     * Shut down the connection.  Any queued messages will be sent.
     */
    public void close();

    /**
     * Identify this connection for logging purposes.
     *
     * @return this connection's ID number.
     */
    public int id();

    /**
     * Send a message over the connection to whomever is at the other end.
     *
     * @param message  The message to be sent.
     */
    public void sendMsg(Object message);

    /**
     * Turn debug features for this connection on or off.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    public void setDebugMode(boolean mode);
}

