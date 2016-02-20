package org.elkoserver.foundation.actor;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.server.metadata.AuthDesc;

/**
 * Interface to be implemented by actor classes making use of the {@link
 * BasicProtocolHandler} message handler implementation, or handlers derived
 * from it.  The {@link BasicProtocolHandler} requires the methods defined in
 * this interface to be available to it as callbacks on the Actor for whom it
 * is providing services.
 */
public interface BasicProtocolActor extends Deliverer {
    /**
     * Authorize (or refuse authorization for) a connection for this actor.
     *
     * @param handler  Handler requesting the authorization.
     * @param auth  Authorization information from the authorization request
     *    message, or null if no authorization information was provided.
     * @param label  Label (e.g., displayable user name) string from the
     *    authorization request message, or "&lt;anonymous&gt;" if no value was
     *    specified.
     *
     * @return true if the authorization succeeded and the session should be
     *    allowed to proceed, false if it did not and the session should be
     *    disconnected.
     */
    boolean doAuth(BasicProtocolHandler handler, AuthDesc auth, String label);

    /**
     * Disconnect this actor.
     */
    void doDisconnect();
}
