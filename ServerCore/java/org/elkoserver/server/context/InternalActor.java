package org.elkoserver.server.context;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for an internal connection to a context server from within the server
 * farm.  Such connnections may send messages to any addressable object but do
 * not have associated users and are not placed into any context.
 */
public class InternalActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private InternalActorFactory myFactory;

    /** Flag that connection has been authorized. */
    private boolean amAuthorized;

    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  Factory of the listener that accepted the connection.
     * @param appTrace  Trace object for diagnostics.
     */
    InternalActor(Connection connection, InternalActorFactory factory,
                  Trace appTrace)
    {
        super(connection, factory.contextor());
        myFactory = factory;
        amAuthorized = false;
        tr = appTrace;
        myLabel = null;
    }

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        doDisconnect();
        tr.eventm(this + " connection died: " + connection + " " + reason);
    }

    /**
     * Do the actual work of authorizing an actor.
     */
    public boolean doAuth(BasicProtocolHandler handler, AuthDesc auth,
                          String label)
    {
        myLabel = label;
        amAuthorized = myFactory.verifyInternalAuthorization(auth);
        return amAuthorized;
    }

    /**
     * Do the actual work of disconnecting an actor.
     */
    public void doDisconnect() {
        tr.eventm("disconnecting " + this);
        close();
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do it.
     */
    public void ensureAuthorized() throws MessageHandlerException {
        if (!amAuthorized) {
            throw new MessageHandlerException("internal connection " + this +
                " attempted operation without authorization");
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
