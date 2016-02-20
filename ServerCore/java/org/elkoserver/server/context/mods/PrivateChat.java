package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

/**
 * Mod to enable users in a context to chat privately with each other.  This
 * mod must be attached to a user, but note that it is not to be attached to
 * the user record in the object database.  It never persists, but is always
 * attached dynamically by a {@link Chat} mod attached to the context.
 *
 * @see Chat
 */
public class PrivateChat extends Mod implements UserMod {
    /** Users can chat privately. */
    private boolean amAllowPrivate;

    /** Users can push URLs. */
    private boolean amAllowPush;

    /* Note that there is no JSON-driven constructor since this mod is never
       loaded from the database. */

    /**
     * Constructor.  Initialization parameters configuring the specific chat
     * operations this mod will enable.
     *
     * Note that setting both 'allowPrivate' and 'allowPush' to false is
     * permitted but not useful.
     *
     * @param allowPrivate  If true, users can chat privately, i.e., transmit
     *    utterances to other individual users.
     * @param allowPush  If true, users can push URLs privately, i.e., to other
     *    individual users.
     */
    public PrivateChat(boolean allowPrivate, boolean allowPush) {
        amAllowPrivate = allowPrivate;
        amAllowPush = allowPush;
    }

    /**
     * Encode this mod for transmission or persistence.  Note that this mod is
     * never persisted or transmitted.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return null since this mod is never persisted or transmitted.
     */
    public JSONLiteral encode(EncodeControl control) {
        return null;
    }

    /**
     * Message handler for the 'push' message.
     *
     * <p>This message pushes a URL to the user this mod is attached to.  This
     * is done by echoing the 'push' message to the target user, marked as
     * being from the user who sent it.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"push", url:<i>STR</i>,
     *                     frame:<i>optSTR</i>, features:<i>optSTR</i>
     *                   } </tt><br>
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
     *    this mod or if the 'allowPush' constructor parameter was false.
     */
    @JSONMethod({ "url", "frame", "features" })
    public void push(User from, String url, OptString frame,
                     OptString features)
        throws MessageHandlerException
    {
        if (amAllowPush) {
            ensureSameContext(from);
            if (!context().isSemiPrivate()) {
                User who = (User) object();
                JSONLiteral response =
                    Msg.msgPush(who, from, url, frame.value(null),
                                features.value(null));
                who.send(response);
                if (from != who) {
                    from.send(response);
                }
            }
        } else {
            throw new MessageHandlerException("private push not allowed");
        }
    }

    /**
     * Message handler for the 'say' message.
     *
     * <p>This message transmits chat text to the user this mod is attached to.
     * This is done by echoing the 'say' message to the target user, marked as
     * being from the user who sent it.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"say", text:<i>STR</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"say", text:<i>STR</i>,
     *                     from:<i>REF</i> } </tt>
     *
     * @param text  The chat text being "spoken".
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if the 'allowPrivate' constructor parameter was false.
     */
    @JSONMethod({ "text" })
    public void say(User from, String text) throws MessageHandlerException {
        if (amAllowPrivate) {
            ensureSameContext(from);
            if (!context().isSemiPrivate()) {
                User who = (User) object();
                JSONLiteral response = Msg.msgSay(who, from, text);
                who.send(response);
                if (from != who) {
                    from.send(response);
                }
            }
        } else {
            throw new MessageHandlerException("private chat not allowed");
        }
    }

}
