package org.elkoserver.server.context;

import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to associate new Users with new Connections.
 */
class UserActorFactory implements MessageHandlerFactory {
    /** The contextor for this server. */
    private Contextor myContextor;

    /** True if reservations are required for entry. */
    private boolean amAuthRequired;

    /** Protocol new connections will be speaking. */
    private String myProtocol;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param contextor  The contextor for this server.
     * @param authRequired  Flag indicating whether reservations are
     *    needed for entry.
     * @param protocol  Protocol these new connections will be speaking
     * @param appTrace  Trace object for diagnostics.
     */
    UserActorFactory(Contextor contextor, boolean authRequired,
                     String protocol, Trace appTrace)
    {
        myContextor = contextor;
        amAuthRequired = authRequired;
        myProtocol = protocol;
        tr = appTrace;
    }

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new UserActor(connection, myContextor, amAuthRequired,
                             myProtocol, tr);
    }
}
