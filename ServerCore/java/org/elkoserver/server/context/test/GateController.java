package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;

/**
 * Mod to enable a context user to control the context's gate
 */
public class GateController extends Mod implements ContextMod {
    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public GateController() {
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
        JSONLiteral result = new JSONLiteral("gate", control);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'gate' message.
     */
    @JSONMethod({ "open", "reason" })
    public void gate(User from, boolean open, OptString optReason)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        if (open) {
            context().openGate();
            from.send(Msg.msgSay(from, from, "gate opened"));
        } else {
            String reason = optReason.value(null);
            context().closeGate(reason);
            from.send(Msg.msgSay(from, from, "gate closed: " + reason));
        }
    }
}
