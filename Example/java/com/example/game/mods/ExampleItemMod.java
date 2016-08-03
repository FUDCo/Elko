package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.CartesianPosition;
import org.elkoserver.server.context.Item;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;

public class ExampleItemMod extends Mod implements ItemMod {
    private String myString1;
    private String myString2;
    private int myInt1;
    private int myInt2;

    @JSONMethod({ "str1", "str2", "int1", "int2" })
    public ExampleItemMod(String string1, OptString optString2, int int1,
                          OptInteger optInt2)
    {
        myString1 = string1;
        myString2 = optString2.value(null);
        myInt1 = int1;
        myInt2 = optInt2.value(0);
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("exi", control);
        result.addParameter("str1", myString1);
        result.addParameterOpt("str2", myString2);
        result.addParameter("int1", myInt1);
        result.addParameter("int2", myInt2);
        result.finish();
        return result;
    }

    @JSONMethod({ "arg", "otherarg" })
    public void itemverb1(User from, String arg, OptString otherArg)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        context().send(msgItemVerb1(context(), from, arg,
                                    otherArg.value(null)));
    }

    @JSONMethod({ "arg", "otherarg" })
    public void itemverb2(User from, String arg, OptString otherArg)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        context().send(msgItemVerb2(context(), arg, otherArg.value(null)));
    }

    static JSONLiteral msgItemVerb1(Referenceable target, Referenceable from,
                                  String arg, String otherArg)
    {
        JSONLiteral msg = new JSONLiteral(target, "itemverb1");
        msg.addParameter("from", from);
        msg.addParameter("arg", arg);
        msg.addParameterOpt("otherarg", otherArg);
        msg.finish();
        return msg;
    }

    static JSONLiteral msgItemVerb2(Referenceable target, String arg,
                                    String otherArg)
    {
        JSONLiteral msg = new JSONLiteral(target, "itemverb2");
        msg.addParameter("arg", arg);
        msg.addParameterOpt("otherarg", otherArg);
        msg.finish();
        return msg;
    }
}
