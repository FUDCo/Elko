package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserWatcher;

/**
 * A simple context mod to enable users in a context to chat with
 * each other.
 */
public class SimpleChat extends Mod
    implements ObjectCompletionWatcher, ContextMod
{
    /** Whether users are permitted to push URLs to other users. */
    private boolean amAllowingPush;

    @JSONMethod({ "allowpush" })
    public SimpleChat(OptBoolean allowPush) {
        amAllowingPush = allowPush.value(false);
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("schat", control);
        if (!control.toClient()) {
            result.addParameter("allowpush", amAllowingPush);
        }
        result.finish();
        return result;
    }

    public void objectIsComplete() {
        context().registerUserWatcher(
            new UserWatcher() {
                public void noteUserArrival(User who) {
                    PrivateChat privateChat = new PrivateChat();
                    privateChat.attachTo(who);
                }
                public void noteUserDeparture(User who) { }
            }
        );
    }

    @JSONMethod({ "url", "frame" })
    public void push(User from, String url, OptString frame)
        throws MessageHandlerException
    {
        if (amAllowingPush) {
            ensureSameContext(from);
            context().send(msgPush(context(), from, url, frame.value(null)));
        } else {
            throw new MessageHandlerException("push not allowed here");
        }
    }

    @JSONMethod({ "speech" })
    public void say(User from, String speech)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        context().send(msgSay(context(), from, speech));
    }

    static JSONLiteral msgPush(Referenceable target, Referenceable from,
                               String url, String frame)
    {
        JSONLiteral msg = new JSONLiteral(target, "push");
        msg.addParameter("from", from);
        msg.addParameter("url", url);
        msg.addParameterOpt("frame", frame);
        msg.finish();
        return msg;
    }

    static JSONLiteral msgSay(Referenceable target, Referenceable from,
                              String speech)
    {
        JSONLiteral msg = new JSONLiteral(target, "say");
        msg.addParameter("from", from);
        msg.addParameter("speech", speech);
        msg.finish();
        return msg;
    }
}
