package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

/**
 * Mod to hold a user's current chat text display style settings.  This is used
 * to allow text chat to be conducted with user-configurable styled text.  It
 * operates in conjunction with {@link TalkOptions} and {@link Chat} mods that
 * should be attached to the context the user is in.
 *
 * This mod gets attached to a user, but note that it is not normally attached
 * to the user record in the object database.  It does not persist, but instead
 * is attached dynamically by the {@link TalkOptions} mod.
 */
public class TalkPrefs
    extends Mod
    implements ObjectCompletionWatcher, UserMod
{
    /** Current style settings. */
    private StyleDesc myStyle;

    /**
     * JSON-driven constructor.  Note that although this method takes a 'style'
     * parameter, normally styles are initialized (in the {@link
     * #objectIsComplete} method) by choosing, in a round-robin fashion, from
     * the style options available in in the context's {@link TalkOptions} mod.
     *
     * @param style  The {@link StyleDesc} associated with the chat text of the
     *    user to whom this mod is attached.
     */
    @JSONMethod({ "style" })
    public TalkPrefs(StyleDesc style) {
        myStyle = style;
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
        if (control.toClient()) {
            JSONLiteral result = new JSONLiteral("talkprefs", control);
            result.addParameter("style", myStyle);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Upon completion of the user object to which this mod attached, grab the
     * next set of available style choices from the context's {@link
     * TalkOptions} mod.
     *
     * <p>Application code should not call this method.
     */
    public void objectIsComplete() {
        TalkOptions rules = (TalkOptions) context().getMod(TalkOptions.class);
        if (rules != null) {
            myStyle = rules.newStyle();
        }
    }

    /**
     * Message handler for the 'style' message.
     *
     * <p>This is a request from a client to change one or more of the style
     * attributes.  If the change operation is successful, a corresponding
     * 'style' message is broadcast to the context.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"style", color:<i>optSTR</i>,
     *                     backgroundColor:<i>optSTR</i>, icon:<i>optSTR</i>,
     *                     textStyle:<i>optSTR</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"style", color:<i>optSTR</i>,
     *                     backgroundColor:<i>optSTR</i>, icon:<i>optSTR</i>,
     *                     textStyle:<i>optSTR</i> } </tt>
     *
     * @param from  The user who sent the message.
     * @param color  New text color value (optional).
     * @param backgroundColor  New background color value (optional).
     * @param icon  New icon URL string (optional).
     * @param textStyle  New typeface/style info (optional).
     *
     * @throws MessageHandlerException if 'from' is not in the same user this
     *    mod is attached to.
     */
    @JSONMethod({ "color", "backgroundColor", "icon", "textStyle" })
    public void style(User from, OptString color, OptString backgroundColor,
                      OptString icon, OptString textStyle)
        throws MessageHandlerException
    {
        ensureSameUser(from);
        String newColor = color.value(null);
        String newBackgroundColor = backgroundColor.value(null);
        String newIcon = icon.value(null);
        String newTextStyle = textStyle.value(null);
        StyleDesc style =
            new StyleDesc(newColor == null ? myStyle.color() : newColor,
                          newBackgroundColor == null ?
                              myStyle.backgroundColor() : newBackgroundColor,
                          null,
                          newTextStyle == null ?
                              myStyle.textStyle(): newTextStyle,
                          newIcon == null ? myStyle.icon() : newIcon);
        TalkOptions rules = (TalkOptions) context().getMod(TalkOptions.class);
        if (rules == null || rules.allowedStyle(style)) {
            myStyle = style;
            context().send(msgStyle(object(), newColor, newBackgroundColor,
                                     newIcon, newTextStyle));
        }
    }

    /**
     * Create a 'style' message.
     *
     * @param target  Object the message is being sent to.
     * @param color  New text color value, or null if not being changed.
     * @param backgroundColor  New background color value, or null if not
     *    being changed
     * @param icon  New icon URL string, or null if not being changed.
     * @param textStyle  New typeface/style info, or null if not being changed.
     */
    static JSONLiteral msgStyle(Referenceable target, String color,
        String backgroundColor, String icon, String textStyle)
    {
        JSONLiteral msg = new JSONLiteral(target, "style");
        msg.addParameterOpt("color", color);
        msg.addParameterOpt("backgroundColor", backgroundColor);
        msg.addParameterOpt("icon", icon);
        msg.addParameterOpt("textStyle", textStyle);
        msg.finish();
        return msg;
    }
}
