package org.elkoserver.server.gatekeeper;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a possibly multi-faceted connection to a gatekeeper.
 */
class GatekeeperActor extends RoutingActor implements BasicProtocolActor
{
    /** Factory holding listener configuration information. */
    private GatekeeperActorFactory myFactory;

    /** True if actor has been disconnected. */
    private boolean amLoggedOut;

    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** True if actor is authorized to perform admin operations. */
    private boolean amAdmin;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Timeout for kicking off users who connect and don't either request a
        reservation or authenticate as an administrator. */
    private Timeout myActionTimeout;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param factory  The factory that created this actor.
     * @param actionTime  How long the user has to act before being kicked off,
     *    in milliseconds.
     * @param appTrace  Trace object for diagnostics.
     */
    GatekeeperActor(Connection connection, GatekeeperActorFactory factory,
                    int actionTime, Trace appTrace)
    {
        super(connection, factory.refTable());
        tr = appTrace;
        myFactory = factory;
        amLoggedOut = false;
        amAdmin = false;
        myLabel = null;
        myActionTimeout = Timer.theTimer().after(
            actionTime,
            new TimeoutNoticer() {
                public void noticeTimeout() {
                    if (myActionTimeout != null) {
                        myActionTimeout = null;
                        doDisconnect();
                    }
                }
            });
    }

    /**
     * Cancel the reservation timeout, because the user is real.
     */
    void becomeLive() {
        if (myActionTimeout != null) {
            myActionTimeout.cancel();
            myActionTimeout = null;
        }
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
     * Do the actual work of authorizing an administrator.
     *
     * After a call to this method returns true, this actor will be an
     * authorized administrator, meaning that {@link #isAdmin} will return true
     * and {@link #ensureAuthorizedAdmin} will succeed without throwing an
     * exception.
     *
     * This method is invoked in response to receipt of an "auth" message by
     * the message handler in {@link BasicProtocolActor}.
     *
     * @param handler  The handler that is requesting the authorization (not
     *    used here).
     * @param auth  Authorization information from the authorization request.
     * @param label  The label string from the authorization request.
     *
     * @return true if the given arguments are sufficient to authorize
     *    administrative access to this server, false if not.
     */
    public boolean doAuth(BasicProtocolHandler handler, AuthDesc auth,
                          String label)
    {
        becomeLive();
        myLabel = label;
        if (myFactory.verifyAuthorization(auth) && myFactory.allowAdmin()) {
            amAdmin = true;
        }
        return amAdmin;
    }

    /**
     * Disconnect this actor from the server.
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
     * 
     * @throws MessageHandlerException if this actor is not authorized to
     *    perform administrative operations.
     *
     * @see #doAuth
     */
    void ensureAuthorizedAdmin() throws MessageHandlerException {
        if (!amAdmin) {
            doDisconnect();
            throw new MessageHandlerException("actor " + this +
                " attempted admin operation without authorization");
        }
    }

    /**
     * Test if this actor is an authenticated administrator.
     *
     * @return true if this actor is an authenticated administrator.
     *
     * @see #doAuth
     */
    boolean isAdmin() {
        return amAdmin;
    }

    /**
     * Get this actor's label.
     *
     * @return the label string for this actor.
     */
    String label() {
        return myLabel;
    }

    /**
     * Get a printable representation of this actor.
     *
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
