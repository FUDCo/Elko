package org.elkoserver.server.context;

import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.ArgRunnable;

/**
 * Interface implemented by objects that can produce User objects on demand,
 * either by manufacturing them from whole cloth or by fetching information
 * from an external data store.  Such factory objects are typically singletons
 * stored in the contextor's static object table.  These factories are used in
 * conjuction with the user synthesis entry pattern provided by the Session
 * object's extercontext() method.
 *
 * @see EphemeralUserFactory
 */
public interface UserFactory {
    /**
     * Produce a user object.
     *
     * @param contextor  The contextor of the server in which the requested
     *    user will be present
     * @param connection  The connection over which the new user presented
     *    themself.
     * @param param  Arbitary JSON object parameterizing the construction.
     *    this is analogous to the user record read from the ODB, but may be
     *    anything that makes sense for the particular factory implementation.
     *    Of course, the sender of this parameter must be coordinated with the
     *    factory implementation.
     * @param handler  Handler to be called with the result.  The result will
     *    be the user object that was produced, or null if none could be.
     */
    void provideUser(Contextor contextor, Connection connection,
                     JSONObject param, ArgRunnable handler);
}
