package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;

/**
 * An empty context mod, to get you started.
 */
public class ExampleContextMod extends Mod implements ContextMod
{
    @JSONMethod
    public ExampleContextMod() {
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("exc", control);
        result.finish();
        return result;
    }

    @JSONMethod({ "arg", "otherarg" })
    public void ctxverb(User from, String arg, OptString otherArg)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        context().send(msgCtxVerb(context(), from, arg, otherArg.value(null)));
    }

    static JSONLiteral msgCtxVerb(Referenceable target, Referenceable from,
                                  String arg, String otherArg)
    {
        JSONLiteral msg = new JSONLiteral(target, "ctxverb");
        msg.addParameter("from", from);
        msg.addParameter("arg", arg);
        msg.addParameterOpt("otherarg", otherArg);
        msg.finish();
        return msg;
    }
}
