package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

/**
 * An empty user mod, to get you started.
 */
public class ExampleUserMod extends Mod implements UserMod {

    @JSONMethod
    public ExampleUserMod() {
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("exu", control);
        result.finish();
        return result;
    }

    @JSONMethod({ "arg", "otherarg" })
    public void userverb(User from, String arg, OptString otherArg)
        throws MessageHandlerException
    {
        ensureSameUser(from);
        JSONLiteral response = msgUserVerb(from, arg, otherArg.value(null));
        from.send(response);
    }

    static JSONLiteral msgUserVerb(Referenceable target, String arg,
                                   String otherArg)
    {
        JSONLiteral msg = new JSONLiteral(target, "userverb");
        msg.addParameter("arg", arg);
        msg.addParameterOpt("otherarg", otherArg);
        msg.finish();
        return msg;
    }
}
