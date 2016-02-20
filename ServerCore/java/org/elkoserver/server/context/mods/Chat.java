package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserWatcher;

/**
 * Mod to enable users in a context to chat with each other.  This mod must be
 * attached to a context.
 *
 * @see PrivateChat
 */
public class Chat extends Mod implements ObjectCompletionWatcher, ContextMod {
    /* Persistent state, initialized from database. */

    /** Users can chat publicly. */
    private boolean amAllowChat;

    /** Users can chat privately. */
    private boolean amAllowPrivate;

    /** Users can push URLs publicly. */
    private boolean amAllowPush;

    /** Users can push URLs privately. */
    private boolean amAllowPrivatePush;

    /**
     * JSON-driven constructor.  Initialization parameters configuring the
     * specific chat operations this mod will enable.
     *
     * <p>If 'allowPrivate' or 'allowPrivatePush' is true, this mod will
     * automatically attach a correspondingly configured ephemeral {@link
     * PrivateChat} mod to any user who enters the context.
     *
     * <p>Note that setting all four 'allow' parameters to false is permitted
     * but not useful.
     *
     * @param allowChat  If true, users can chat publicly, i.e., issue
     *    utterances that are broadcast to everyone in the context.
     * @param allowPrivate  If true, users can chat privately, i.e., transmit
     *    utterances to other individual users.
     * @param allowPush  If true, users can push URLs publicly, i.e., to
     *    everyone in the context.
     * @param allowPrivatePush  If true, users can push URLs privately, i.e.,
     *    to other individual users.
     */
    @JSONMethod({ "allowchat", "allowprivate", "allowpush",
                  "allowprivatepush" })
    public Chat(OptBoolean allowChat, OptBoolean allowPrivate,
                OptBoolean allowPush, OptBoolean allowPrivatePush)
    {
        amAllowChat = allowChat.value(true);
        amAllowPrivate = allowPrivate.value(true);
        amAllowPush = allowPush.value(true);
        amAllowPrivatePush =
            allowPrivatePush.value(amAllowPrivate && amAllowPush);
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
            JSONLiteral result = new JSONLiteral("chat", control);
            if (!amAllowChat) {
                result.addParameter("allowchat", amAllowChat);
            }
            if (!amAllowPrivate) {
                result.addParameter("allowprivate", amAllowPrivate);
            }
            if (!amAllowPush) {
                result.addParameter("allowpush", amAllowPush);
            }
            if (!amAllowPrivatePush) {
                result.addParameter("allowprivatepush", amAllowPrivatePush);
            }
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * If this mod's configuration enables private chat and/or private push,
     * arrange to automatically attach ephemeral {@link PrivateChat} mods to
     * arriving users.
     *
     * <p>Application code should not call this method.
     */
    public void objectIsComplete() {
        if (amAllowPrivate || amAllowPrivatePush) {
            context().registerUserWatcher(
                new UserWatcher() {
                    public void noteUserArrival(User who) {
                        PrivateChat privateChat =
                            new PrivateChat(amAllowPrivate,
                                            amAllowPrivatePush);
                        privateChat.attachTo(who);
                    }
                    public void noteUserDeparture(User who) { }
                });
        }
    }

    /**
     * Message handler for the 'push' message.
     *
     * <p>This message pushes a URL to everyone in the context.  This is done
     * by echoing the 'push' message to the context, marked as being from the
     * user who sent it.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"push", url:<i>STR</i>,
     *                     frame:<i>optSTR</i>,
     *                     features:<i>optSTR</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"push", url:<i>STR</i>,
     *                     frame:<i>optSTR</i>, features:<i>optSTR</i>,
     *                     from:<i>REF</i> } </tt>
     *
     * @param url  The URL being pushed.
     * @param frame  Optional name of a frame to push it to.
     * @param features  Optional features string to associate with it.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if the 'allowPush' configuration was parameter false.
     */
    @JSONMethod({ "url", "frame", "features" })
    public void push(User from, String url, OptString frame,
                     OptString features)
        throws MessageHandlerException
    {
        if (amAllowPush) {
            ensureSameContext(from);
            JSONLiteral response =
                Msg.msgPush(context(), from, url, frame.value(null),
                            features.value(null));
            if (context().isSemiPrivate()) {
                from.send(response);
            } else {
                context().send(response);
            }
        } else {
            throw new MessageHandlerException("push not allowed");
        }
    }

    /**
     * Message handler for the 'say' message.
     *
     * <p>This message broadcasts chat text to everyone in the context.  This
     * is done by echoing the 'say' message to the context, marked as being
     * from the user who sent it.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"say", text:<i>STR</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"say", text:<i>STR</i>,
     *                     from:<i>fromREF</i> } </tt>
     *
     * @param text  The chat text being "spoken".
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if the 'allowChat' configuration parameter was false.
     */
    @JSONMethod({ "text" })
    public void say(User from, String text) throws MessageHandlerException {
        if (amAllowChat) {
            ensureSameContext(from);
            JSONLiteral response = Msg.msgSay(context(), from, text);
            if (context().isSemiPrivate()) {
                from.send(response);
            } else {
                context().send(response);
            }
        } else {
            throw new MessageHandlerException("chat not allowed");
        }
    }
}
