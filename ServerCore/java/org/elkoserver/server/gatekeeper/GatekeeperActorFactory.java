package org.elkoserver.server.gatekeeper;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * MessageHandlerFactory class to create new actors for new connections to a
 * gatekeeper's listen port.
 */
class GatekeeperActorFactory implements MessageHandlerFactory {
    /** The gatekeeper itself. */
    private Gatekeeper myGatekeeper;

    /** What kind of authorization is required. */
    private AuthDesc myAuth;

    /** Flag that admin connections are allowed. */
    private boolean amAllowAdmin;

    /** Flag that user connections are allowed. */
    private boolean amAllowUser;

    /** How long a user has to act before being kicked off, in milliseconds. */
    private int myActionTimeout;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param gatekeeper  The gatekeeper itself.
     * @param auth  The authorization needed for connections to this port.
     * @param allowAdmin  If true, allow 'admin' connections.
     * @param appTrace  Trace object for diagnostics.
     */
    GatekeeperActorFactory(Gatekeeper gatekeeper, AuthDesc auth,
                           boolean allowAdmin, boolean allowUser,
                           int actionTimeout, Trace appTrace)
    {
        myGatekeeper = gatekeeper;
        myAuth = auth;
        amAllowAdmin = allowAdmin;
        amAllowUser = allowUser;
        myActionTimeout = actionTimeout;
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
     * Test whether user connections are allowed.
     *
     * @return true if 'user' connections are allowed.
     */
    boolean allowUser() {
        return amAllowUser;
    }

    /**
     * Get this factory's gatekeeper.
     *
     * @return the gatekeeper object this factory uses.
     */
    Gatekeeper gatekeeper() {
        return myGatekeeper;
    }

    /**
     * Produce a new user for a new connection.
     *
     * @param connection  The new connection.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new GatekeeperActor(connection, this, myActionTimeout, tr);
    }

    /**
     * Get this factory's ref table.
     *
     * @return the object ref table this factory uses.
     */
    RefTable refTable() {
        return myGatekeeper.refTable();
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
