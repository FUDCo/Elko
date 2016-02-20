package org.elkoserver.server.director;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for a connection to a director.  An actor may be associated with any
 * or all of the three service protocols offered by a director ('admin',
 * 'provider', and 'user'), according to the permissions granted by the
 * factory.
 */
class DirectorActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private DirectorActorFactory myFactory;

    /** The director itself. */
    private Director myDirector;

    /** True if actor has been disconnected. */
    private boolean amLoggedOut;

    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** Admin object if this actor is an admin, else null. */
    private Admin myAdmin;

    /** Provider object if this actor is a provider, else null. */
    private Provider myProvider;

    /** True if this actor is a user. */
    private boolean amUser;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  Factory of the listener that accepted the connection.
     * @param appTrace  Trace object for diagnostics.
     */
    DirectorActor(Connection connection, DirectorActorFactory factory,
                  Trace appTrace)
    {
        super(connection, factory.refTable());
        tr = appTrace;
        myFactory = factory;
        myDirector = factory.director();
        myLabel = null;
        amLoggedOut = false;
        myAdmin = null;
        myProvider = null;
        amUser = false;
    }

    /**
     * Get this actor's admin facet.
     *
     * @return the Admin object associated with this actor, or null if this
     *    actor isn't an admin.
     */
    Admin admin() {
        return myAdmin;
    }

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        doDisconnect();
        tr.eventm(this + " connection died: " + connection);
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    public boolean doAuth(BasicProtocolHandler handler, AuthDesc auth,
                          String label)
    {
        myLabel = label;
        boolean success = false;
        if (myFactory.verifyAuthorization(auth)) {
            if (handler instanceof AdminHandler) {
                if (myAdmin == null && myFactory.allowAdmin()) {
                    myAdmin = new Admin(myDirector, this);
                    success = true;
                } else {
                    tr.warningi("auth failed: admin access not allowed");
                }
            } else if (handler instanceof ProviderHandler) {
                if (myProvider == null && myFactory.allowProvider() &&
                        !myDirector.isFull()) {
                    myProvider = new Provider(myDirector, this, tr);
                    success = true;
                } else {
                    tr.warningi("auth failed: provider access not allowed");
                }
            } else if (handler instanceof UserHandler) {
                if (!amUser && myFactory.allowUser()) {
                    amUser = true;
                    success = true;
                } else {
                    tr.warningi("auth failed: user access not allowed");
                }
            } else {
                tr.errorm("auth failed: unknown handler type");
            }
        } else {
            tr.warningi("auth failed: credential verification failure");
        }
        return success;
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    public void doDisconnect() {
        if (!amLoggedOut) {
            tr.eventm("disconnecting " + this);
            if (myProvider != null) {
                myProvider.doDisconnect();
            }
            if (myAdmin != null) {
                myAdmin.doDisconnect();
            }
            amLoggedOut = true;
            close();
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     */
    void ensureAuthorizedAdmin() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted admin operation after logout");
        } else if (myAdmin == null) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted admin operation without authorization");
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do provider operations.
     */
    void ensureAuthorizedProvider() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted provider operation after logout");
        } else if (myProvider == null) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted provider operation without authorization");
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do user operations.
     */
    void ensureAuthorizedUser() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted user operation after logout");
        } else if (!amUser && myProvider == null && myAdmin == null) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted user operation without authorization");
        }
    }

    /**
     * Test if this actor corresponds to a connection from within the
     * server farm, i.e., that it is not just a user.
     */
    boolean isInternal() {
        return myProvider != null;
    }

    /**
     * Return this actor's label.
     */
    String label() {
        return myLabel;
    }

    /**
     * Get this actor's provider facet.
     *
     * @return the Provider object associated with this actor, or null if this
     *    actor isn't a provider.
     */
    public Provider provider() {
        return myProvider;
    }

    /**
     * @return a printable representation of this actor.
     */
    public String toString() {
        if (myLabel == null) {
            return super.toString();
        } else {
            return myLabel;
        }
    }
}
