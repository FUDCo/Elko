package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;

/**
 * Mod to hold a free-floating chunk of text.  This mod must be attached to an
 * item, not to a user or context.
 */
public class Note extends Mod implements ItemMod {
    /* Persistent state, initialized from database. */

    /** What it says. */
    private String myText;

    /** How it says it. */
    private StyleDesc myStyle;

    /**
     * JSON-driven constructor.
     *
     * @param text  The text of this note.
     * @param style  How its text is to be displayed.
     */
    @JSONMethod({ "text", "style" })
    public Note(String text, StyleDesc style) {
        myText = text;
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
        JSONLiteral result = new JSONLiteral("note", control);
        result.addParameter("text", myText);
        result.addParameterOpt("style", myStyle);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'edit' message.
     *
     * <p>This message is a request from a client to change the text of this
     * note or one or more of its style attributes.  If the change is
     * successful, a corresponding 'edit' message is broadcast to the
     * context.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"edit", text:<i>optSTR</i>,
     *                     style:<i>optSTYLE</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"edit", text:<i>optSTR</i>,
     *                     style:<i>optSTYLE</i> } </tt>
     *
     * @param from  The user who sent the message.
     * @param text  New text string value (optional).
     * @param style  New style information (optional).
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if invalid style information is provided.
     */
    @JSONMethod({ "text", "?style" })
    public void edit(User from, OptString text, StyleDesc style)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        if (style != null) {
            style = myStyle.mergeStyle(style);
            NoteMaker rules = (NoteMaker) context().getMod(NoteMaker.class);
            if (rules != null && rules.allowedStyle(style)) {
                myStyle = style;
            } else {
                throw new MessageHandlerException("invalid style choice");
            }
        }
        String newText = text.value(null);
        if (newText != null) {
            myText = newText;
        }
        if (style != null || newText != null) {
            markAsChanged();
            context().send(msgEdit(object(), newText, style));
        }
    }

    /**
     * Create a 'edit' message.
     *
     * @param target  Object the message is being sent to.
     * @param text  New text string value, or null if not being changed.
     * @param style  New style information, or null if not being changed.
     */
    static JSONLiteral msgEdit(Referenceable target, String text,
                               StyleDesc style)
    {
        JSONLiteral msg = new JSONLiteral(target, "edit");
        msg.addParameterOpt("text", text);
        msg.addParameterOpt("style", style);
        msg.finish();
        return msg;
    }
}
