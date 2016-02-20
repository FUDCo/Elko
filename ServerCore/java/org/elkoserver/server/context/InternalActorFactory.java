package org.elkoserver.server.context;

import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * context server's internal listen port.
 */
class InternalActorFactory implements MessageHandlerFactory {
    /** The contextor for this server itself. */
    private Contextor myContextor;

    /** What kind of authorization is required. */
    private AuthDesc myAuth;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param contextor  The contextor itself.
     * @param auth  The authorization needed for connections to this port.
     * @param appTrace  Trace object for diagnostics.
     */
    InternalActorFactory(Contextor contextor, AuthDesc auth, Trace appTrace) {
        myContextor = contextor;
        myAuth = auth;
        tr = appTrace;
    }

    /**
     * Get this factory's contextor.
     *
     * @return the contextor object this factory uses.
     */
    Contextor contextor() {
        return myContextor;
    }

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new InternalActor(connection, this, tr);
    }

    /**
     * Check the actor's authorization.
     *
     * @param auth  Authorization being used.
     *
     * @return true if 'auth' correctly authorizes connection under
     *    this factory's authorization configuration.
     */
    boolean verifyInternalAuthorization(AuthDesc auth) {
        return myAuth.verify(auth);
    }
}
