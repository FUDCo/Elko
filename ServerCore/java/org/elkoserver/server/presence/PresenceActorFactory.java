package org.elkoserver.server.presence;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to create actors for new connections to a
 * presence server's listen port.
 */
class PresenceActorFactory implements MessageHandlerFactory {
    /** The presence server proper. */
    private PresenceServer myPresenceServer;

    /** What kind of authorization is required. */
    private AuthDesc myAuth;

    /** Flag that admin connections are allowed. */
    private boolean amAllowAdmin;

    /** Flag that presence server client connections are allowed. */
    private boolean amAllowClient;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param presenceServer  The presence server itself.
     * @param auth  The authorization needed for connections to this port.
     * @param allowAdmin  If true, allow 'admin' connections.
     * @param allowClient  If true, allow 'client' connections.
     * @param appTrace  Trace object for diagnostics.
     */
    PresenceActorFactory(PresenceServer presenceServer, AuthDesc auth,
        boolean allowAdmin, boolean allowClient, Trace appTrace)
    {
        myPresenceServer = presenceServer;
        myAuth = auth;
        amAllowAdmin = allowAdmin;
        amAllowClient = allowClient;
        tr = appTrace;
    }

    /**
     * Test whether admin connections are allowed.
     *
     * @return true if 'admin' connections are allowed.
     */
    boolean allowAdmin() {
        return amAllowAdmin;
    }

    /**
     * Test whether client connections are allowed.
     *
     * @return true if 'client' connections are allowed.
     */
    boolean allowClient() {
        return amAllowClient && !myPresenceServer.isShuttingDown();
    }

    /**
     * Get this factory's presence server.
     *
     * @return the presence server object this factory uses.
     */
    PresenceServer presenceServer() {
        return myPresenceServer;
    }

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new PresenceActor(connection, this, tr);
    }

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    RefTable refTable() {
        return myPresenceServer.refTable();
    }

    /**
     * Check the actor's authorization.
     *
     * @param auth  Authorization being used.
     *
     * @return true if 'auth' correctly authorizes connection under
     *    this factory's authorization configuration.
     */
    boolean verifyAuthorization(AuthDesc auth) {
        return myAuth.verify(auth);
    }
}
