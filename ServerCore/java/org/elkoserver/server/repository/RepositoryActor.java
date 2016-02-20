package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for a connection to a repository.  An actor may be associated with
 * either or both of the two service protocols offered by a repository ('admin'
 * and 'rep'), according to the permissions granted by the factory.
 */
class RepositoryActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private RepositoryActorFactory myFactory;

    /** The repository itself. */
    private Repository myRepository;

    /** True if actor has been disconnected. */
    private boolean amLoggedOut;

    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** True if actor is authorized to perform admin operations. */
    private boolean amAdmin;

    /** True if actor is authorized to perform repository operations. */
    private boolean amRep;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  The factory that created this actor.
     * @param appTrace  Trace object for diagnostics.
     */
    RepositoryActor(Connection connection, RepositoryActorFactory factory,
                    Trace appTrace)
    {
        super(connection, factory.refTable());
        tr = appTrace;
        myFactory = factory;
        myRepository = factory.repository();
        myLabel = null;
        amLoggedOut = false;
        amAdmin = false;
        amRep = false;
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
            } else if (handler instanceof RepHandler) {
                if (myFactory.allowRep()) {
                    amRep = true;
                    success = true;
                    myRepository.countRepClients(1);
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
            if (amRep) {
                myRepository.countRepClients(-1);
            }
            amLoggedOut = true;
            close();
        }
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     * 
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
     * actor who is authorized to do repository operations.
     * 
     */
    public void ensureAuthorizedRep() throws MessageHandlerException {
        if (amLoggedOut) {
            throw new MessageHandlerException("actor " + this +
                " attempted repository operation after logout");
        } else if (!amRep) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted repository operation without authorization");
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
