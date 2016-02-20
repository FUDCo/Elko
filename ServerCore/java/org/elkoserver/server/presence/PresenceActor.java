package org.elkoserver.server.presence;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for a connection to a presence server.  An actor may be associated
 * with either or both of the two service protocols offered by a presence
 * server ('admin' and 'presence'), according to the permissions granted by the
 * factory.
 */
class PresenceActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private PresenceActorFactory myFactory;

    /** The presence server itself. */
    private PresenceServer myPresenceServer;

    /** True if actor has been disconnected. */
    private boolean amLoggedOut;

    /** Label for logging and such. */
    private String myLabel;

    /** Client object if this actor is a client, else null. */
    private Client myClient;

    /** True if actor is authorized to perform admin operations. */
    private boolean amAdmin;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  The factory that created this actor.
     * @param appTrace  Trace object for diagnostics.
     */
    PresenceActor(Connection connection, PresenceActorFactory factory,
                Trace appTrace)
    {
        super(connection, factory.refTable());
        tr = appTrace;
        myFactory = factory;
        myPresenceServer = factory.presenceServer();
        myLabel = null;
        amLoggedOut = false;
        amAdmin = false;
        myClient = null;
        myPresenceServer.addActor(this);
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
        boolean success = false;
        if (myFactory.verifyAuthorization(auth)) {
            if (handler instanceof AdminHandler) {
                if (!amAdmin && myFactory.allowAdmin()) {
                    amAdmin = true;
                    myLabel = label;
                    success = true;
                }
            } else if (handler instanceof ClientHandler) {
                if (myClient == null && myFactory.allowClient()) {
                    myClient = new Client(myPresenceServer, this);
                    myLabel = label;
                    success = true;
                }
            }
        }
        return success;
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    public void doDisconnect() {
        if (!amLoggedOut) {
            tr.eventm("disconnecting " + this);
            if (myClient != null) {
                myClient.doDisconnect();
            }
            myPresenceServer.removeActor(this);
            amLoggedOut = true;
            close();
        }
    }

    /**
     * Get this actor's client facet.
     *
     * @return the Client object associated with this actor, or null if this
     *    actor isn't a client.
     */
    public Client client() {
        return myClient;
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     */
    void ensureAuthorizedAdmin() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted admin operation after logout");
        } else if (!amAdmin) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted admin operation without authorization");
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do client operations.
     */
    void ensureAuthorizedClient() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted client operation after logout");
        } else if (myClient == null) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted client operation without authorization");
        }
    }

    /**
     * Return this actor's label.
     */
    String label() {
        return myLabel;
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
