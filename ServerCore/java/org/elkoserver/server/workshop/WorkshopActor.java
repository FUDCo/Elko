package org.elkoserver.server.workshop;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for a connection to a workshop.  An actor may be associated with
 * either or both of the two service protocols offered by a workshop (admin
 * and client), according to the permissions granted by the factory.
 */
public class WorkshopActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private WorkshopActorFactory myFactory;

    /** The workshop itself. */
    private Workshop myWorkshop;

    /** True if actor has been disconnected. */
    private boolean amLoggedOut;

    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** True if actor is authorized to perform admin operations. */
    private boolean amAdmin;

    /** True if actor is authorized to perform workshop client operations. */
    private boolean amClient;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  The factory that created this actor.
     * @param appTrace  Trace object for diagnostics.
     */
    WorkshopActor(Connection connection, WorkshopActorFactory factory,
                  Trace appTrace)
    {
        super(connection, factory.workshop());
        tr = appTrace;
        myFactory = factory;
        myWorkshop = factory.workshop();
        myLabel = null;
        amLoggedOut = false;
        amAdmin = false;
        amClient = false;
    }

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        doDisconnect();
        tr.eventm(this + " connection died: " + connection + reason);
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
                if (myFactory.allowAdmin()) {
                    amAdmin = true;
                    success = true;
                }
            } else if (handler instanceof ClientHandler) {
                if (myFactory.allowClient()) {
                    amClient = true;
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
            amLoggedOut = true;
            close();
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     */
    public void ensureAuthorizedAdmin() throws MessageHandlerException {
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
     * actor who is authorized to do workshop client operations.
     * 
     */
    public void ensureAuthorizedClient() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted client operation after logout");
        } else if (!amClient) {
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
