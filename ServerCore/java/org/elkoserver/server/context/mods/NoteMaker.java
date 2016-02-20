package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.BasicObject;
import org.elkoserver.server.context.GeneralMod;
import org.elkoserver.server.context.Item;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;

/**
 * Mod to enable creation of notes.  Notes are items with the {@link Note} mod
 * attached.  This mod is generally attached to a context, but this is not
 * required.
 */
public class NoteMaker extends Mod implements GeneralMod {
    /* Persistent state, initialized from database. */

    /** Permitted style attributes. */
    private StyleOptions myStyleOptions;

    /**
     * JSON-driven constructor.
     *
     * @param styleOptions  Style options permitted in notes created by this
     *    mod.
     */
    @JSONMethod({ "styles" })
    public NoteMaker(StyleOptions styleOptions) {
        myStyleOptions = styleOptions;
    }

    /**
     * Test if this mod's style options are compatible with particular style
     * settings.
     *
     * @param style  The style to test.
     *
     * @return true if 'style' is acceptable to this NoteMaker, false if not.
     */
    public boolean allowedStyle(StyleDesc style) {
        return myStyleOptions.allowedStyle(style);
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
        JSONLiteral result = new JSONLiteral("notemaker", control);
        result.addParameter("styles", myStyleOptions);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'makenote' message.
     *
     * <p>This is a request from a client to create a new note.  If the
     * creation operation is successful, a 'make' message will be broadcast to
     * the context, describing the new note object.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"makeNote", into:<i>optREF</i>,
     *                     left:<i>INT</i>, top:<i>INT</i>, width:<i>INT</i>,
     *                     height:<i>INT</i>, text:<i>STR</i>,
     *                     style:<i>optSTYLE</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>intoREF</i>, op:"make", ... } </tt>
     *
     * @param from  The user who sent the message.
     * @param into  Container into which the new note object should be placed
     *    (optional, defaults to the context).
     * @param left  X coordinate of new object relative to container.
     * @param top  Y coordinate of new object relative to container.
     * @param width  Width of note display.
     * @param height  Height of note display.
     * @param text  The text to show.
     * @param style  Style information for 'text' (optional, defaults to
     *    unstyled text).
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod or if invalid style information is provided or if the
     *    proposed container is not valid.
     */
    @JSONMethod({ "into", "left", "top", "width", "height", "text", "?style" })
    public void makenote(User from, OptString into, int left, int top,
                         int width, int height, String text, StyleDesc style)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        String intoRef = into.value(null);
        BasicObject intoObj;
        if (intoRef != null) {
            intoObj = context().get(intoRef);
        } else {
            intoObj = context();
        }
        style = myStyleOptions.mergeStyle(style);
        if (style == null) {
            throw new MessageHandlerException("invalid style options");
        } else if (!Cartesian.validContainer(intoObj, from)) {
            throw new MessageHandlerException(
                "invalid destination container " + intoRef);
        } else {
            Item item = intoObj.createItem("note", false, true);
            Note note = new Note(text, style);
            note.attachTo(item);
            Cartesian cart = new Cartesian(width, height, left, top);
            cart.attachTo(item);
            item.objectIsComplete();
            context().send(Msg.msgMake(intoObj, item, from));
        }
    }
}
