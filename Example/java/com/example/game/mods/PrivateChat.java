package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

/**
 * Ephemeral user mod to let users in a context talk privately to each other.
 */
public class PrivateChat extends Mod implements UserMod {

    @JSONMethod
    public PrivateChat() {
    }

    public JSONLiteral encode(EncodeControl control) {
        return null;
    }

    @JSONMethod({ "speech" })
    public void say(User from, String speech) throws MessageHandlerException {
        ensureSameContext(from);
        User who = (User) object();
        JSONLiteral response = SimpleChat.msgSay(who, from, speech);
        who.send(response);
        if (from != who) {
            from.send(response);
        }
    }
}
