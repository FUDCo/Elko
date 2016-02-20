package org.elkoserver.foundation.net;

/**
 * Interface for objects that handle events on a {@link Connection}.
 *
 * An implementor of this interface is associated with each {@link Connection}
 * object, to handle both incoming messages and disconnection events.
 * Normally, a {@link Connection}'s MessageHandler is produced by a {@link
 * MessageHandlerFactory} when the connection is established.  The factory is
 * held by the {@link NetworkManager} for this purpose.
 */
public interface MessageHandler {
    /**
     * Cope with connection death.  The connection might have been shut down
     * deliberately, the underlying TCP connection might have failed, or an
     * internal error may have killed the connection.  In any event, the
     * connection is dead.  Deal with it.
     *
     * @param connection  The connection that has just died.
     * @param reason  A possible indication why the connection went away.
     */
    public void connectionDied(Connection connection, Throwable reason);

    /**
     * Process an incoming message from a connection.
     *
     * @param connection  The connection upon which the message arrived.
     * @param message  The incoming message.
     */
    public void processMessage(Connection connection, Object message);
}
