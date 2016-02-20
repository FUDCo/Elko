package org.elkoserver.foundation.net;

/**
 * Interface for the object that a {@link Connection} uses to accept incoming
 * messages from the net.
 */
public interface MessageReceiver {
    /**
     * Receive a message from elsewhere.
     *
     * @param message  The message to be received.
     */
    public void receiveMsg(Object message);
}
