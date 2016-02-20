package org.elkoserver.server.context;

import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONObject;

/**
 * Interface implemented by objects that can synthesize ephemeral User objects
 * on demand.  Such factory objects are typically singletons stored in the
 * contextor's static object table.  These factories are used in conjuction
 * with the user synthesis entry pattern provided by the Session object's
 * extercontext() method.  This interface is synchronous, and so is intended to
 * be used when the user can be fabricated entirely from parameter informatio
 * in a non-blocking fashion.
 *
 * @see UserFactory
 */
public interface EphemeralUserFactory {
    /**
     * Synthesize a user object.
     *
     * @param contextor  The contextor of the server in which the synthetic
     *    user will be present
     * @param connection  The connection over which the new user presented
     *    themself.
     * @param param  Arbitary JSON object parameterizing the construction.
     *    this is analogous to the user record read from the ODB, but may be
     *    anything that makes sense for the particular factory implementation.
     *    Of course, the sender of this parameter must be coordinated with the
     *    factory implementation.
     * @param contextRef  Ref of context the new synthesized user will be
     *    placed into
     * @param contextTemplate  Ref of the context template for the context
     *
     * @return a synthesized User object constructed according the the logic of
     *    the factory and the contents of the param parameter, or null if no
     *    such User object could be produced.
     */
    User provideUser(Contextor contextor, Connection connection,
                     JSONObject param, String contextRef,
                     String contextTemplate);
}
