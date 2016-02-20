package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * repository's listen port.
 */
class RepositoryActorFactory implements MessageHandlerFactory {
    /** The repostory proper. */
    private Repository myRepository;

    /** What kind of authorization is required. */
    private AuthDesc myAuth;

    /** Flag that admin connections are allowed. */
    private boolean amAllowAdmin;

    /** Flag that repository connections are allowed. */
    private boolean amAllowRep;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param repository  The repository this factory is making actors for.
     * @param auth  The authorization needed for connections to this port.
     * @param allowAdmin  If true, permit admin connections.
     * @param allowRep  If true, permit repository connections.
     * @param appTrace  Trace object for diagnostics.
     */
    RepositoryActorFactory(Repository repository, AuthDesc auth,
                           boolean allowAdmin, boolean allowRep,
                           Trace appTrace)
    {
        myRepository = repository;
        myAuth = auth;
        amAllowAdmin = allowAdmin;
        amAllowRep = allowRep;
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
     * Test whether repository connections are allowed.
     *
     * @return true if 'rep' connections are allowed.
     */
    boolean allowRep() {
        return amAllowRep && !myRepository.isShuttingDown();
    }

    /**
     * Produce a new actor for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new RepositoryActor(connection, this, tr);
    }

    /**
     * Return the object ref table for this factor.
     */
    RefTable refTable() {
        return myRepository.refTable();
    }

    /**
     * Get this factory's repository.
     *
     * @return the repository object this factory uses.
     */
    Repository repository() {
        return myRepository;
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
