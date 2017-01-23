package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;

/**
 * Mod to enable an item to function as a die.
 */
public class Die extends Mod implements ItemMod {
    /** How many sides this die has. */
    private int mySides;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "sides" })
    public Die(int sides) {
        mySides = sides;
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
        JSONLiteral result = new JSONLiteral("die", control);
        result.addParameter("sides", mySides);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'roll' message.
     *
     * This message requests a die roll.
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"roll" } </tt>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"roll",
     *                     from:<i>REF</i>, value:<i>int</i> } </tt>
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod, or if this mod is attached to a user and 'from' is not that
     *    user.
     */
    @JSONMethod
    public void roll(User from) throws MessageHandlerException {
        ensureSameContext(from);
        JSONLiteral announce = new JSONLiteral(object(), "roll");
        int value = (int) context().contextor().randomLong();
        value = Math.abs(value) % mySides + 1;
        announce.addParameter("value", value);
        announce.addParameter("from", from.ref());
        announce.finish();
        context().send(announce);
    }
}
