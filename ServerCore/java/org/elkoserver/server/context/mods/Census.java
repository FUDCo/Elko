package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.GeneralMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;

/**
 * Mod to enable tracking a context's population.  This mod may be attached to
 * a context, user or item.
 */
public class Census extends Mod implements GeneralMod {
    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public Census() {
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this mod.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral result = new JSONLiteral("census", control);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Message handler for the 'census' message.
     *
     * This message requests the current number of users in the context where
     * this mod resides.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"census" } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"census",
     *                     occupancy:<i>int</i> } </tt>
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod, or if this mod is attached to a user and 'from' is not that
     *    user.
     */
    @JSONMethod
    public void census(User from) throws MessageHandlerException {
        ensureSameContext(from);
        if (object() instanceof User) {
            ensureSameUser(from);
        }
        JSONLiteral response = new JSONLiteral(object(), "census");
        response.addParameter("occupancy", context().userCount());
        response.finish();
        from.send(response);
    }
}
