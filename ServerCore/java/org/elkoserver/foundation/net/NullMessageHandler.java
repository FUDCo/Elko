package org.elkoserver.foundation.net;

import org.elkoserver.util.trace.Trace;

/**
 * "Message handler" for connections that are send only.  No messages will ever
 * be received over such a connection, hence no message handler will actually
 * be needed, but one must be provided to make the comm system happy, hence
 * this placeholder class.  Note that if a message actually *is* received, it
 * is an error, so this class provides an option for logging it.
 */
public class NullMessageHandler implements MessageHandler {
    private Trace tr;

    public NullMessageHandler(Trace trace) {
        tr = trace;
    }

    /**
     * Cope with connection death.  The connection might have been shut down
     * deliberately, the underlying TCP connection might have failed, or an
     * internal error may have killed the connection.  In any event, the
     * connection is dead.  Deal with it.
     *
     * @param connection  The connection that has just died.
     * @param reason  A possible indication why the connection went away.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        if (tr != null) {
            tr.eventm("send-only connection " + connection + " died: " +
                      reason);
        }
    }

    /**
     * Process an incoming message from a connection by complaining to the log
     * if we have configured to talk to the log.
     *
     * @param connection  The connection upon which the message arrived.
     * @param message  The incoming message.
     */
    public void processMessage(Connection connection, Object message) {
        if (tr != null) {
            tr.errorm("message received on allegedly send-only connection " +
                      connection + ": " + message);
        }
    }
}
