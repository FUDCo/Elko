package org.elkoserver.server.director;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * director's listen port.
 */
class DirectorActorFactory implements MessageHandlerFactory {
    /** The director itself. */
    private Director myDirector;

    /** What kind of authorization is required. */
    private AuthDesc myAuth;

    /** Flag that admin connections are allowed. */
    private boolean amAllowAdmin;

    /** Flag that provider connections are allowed. */
    private boolean amAllowProvider;

    /** Flag that user connections are allowed. */
    private boolean amAllowUser;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param director  The director itself.
     * @param auth  The authorization needed for connections to this port.
     * @param allowAdmin  If true, allow 'admin' connections.
     * @param allowProvider  If true, allow 'provider' connections.
     * @param allowUser  If true, allow 'user' connections.
     * @param appTrace  Trace object for diagnostics.
     */
    DirectorActorFactory(Director director, AuthDesc auth, boolean allowAdmin,
                         boolean allowProvider, boolean allowUser,
                         Trace appTrace)
    {
        myDirector = director;
        myAuth = auth;
        amAllowAdmin = allowAdmin;
        amAllowProvider = allowProvider;
        amAllowUser = allowUser;
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
     * Test whether provider connections are allowed.
     *
     * @return true if 'provider' connections are allowed.
     */
    boolean allowProvider() {
        return amAllowProvider && !myDirector.isShuttingDown();
    }

    /**
     * Test whether user connections are allowed.
     *
     * @return true if 'user' connections are allowed.
     */
    boolean allowUser() {
        return amAllowUser;
    }

    /**
     * Get this factory's director.
     *
     * @return the director object this factory uses.
     */
    Director director() {
        return myDirector;
    }

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new DirectorActor(connection, this, tr);
    }

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    RefTable refTable() {
        return myDirector.refTable();
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
