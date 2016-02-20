package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;

/**
 * Mod to hold a context's permissible chat text display style options.  This
 * mod must be attached to a context.  It operates in conjunction with the
 * {@link TalkPrefs} and {@link Chat} mods.
 */
public class TalkOptions extends Mod implements ContextMod {
    /** Current style options. */
    private StyleOptions myStyles;

    /** Counter for allocating style information. */
    private int myCounter;

    /**
     * JSON-driven constructor.
     *
     * @param styles  Permissible styles for chat text in the context to which
     *    this mod is attached.
     */
    @JSONMethod({ "styles" })
    public TalkOptions(StyleOptions styles) {
        myStyles = styles;
        myCounter = 0;
    }

    /**
     * Test if this mod's style options are compatible with particular style
     * settings.
     *
     * @param style  The {@link StyleDesc} to test.
     *
     * @return true if 'style' is acceptable to this object, false if not.
     */
    public boolean allowedStyle(StyleDesc style) {
        return myStyles.allowedStyle(style);
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
        JSONLiteral result = new JSONLiteral("talkoptions", control);
        result.addParameter("styles", myStyles);
        result.finish();
        return result;
    }

    /**
     * Get a set of style settings for a new user.  Successive calls to this
     * method will return a sequence of different style settings that step
     * through the various available style options in a round-robin fashion.
     *
     * @return a new {@link StyleDesc} object suitable for another user in the
     *    context.
     */
    StyleDesc newStyle() {
        String choices[];

        String color;
        choices = myStyles.colors();
        if (choices == null) {
            color = null;
        } else {
            color = choices[myCounter % choices.length];
        }

        String backgroundColor;
        choices = myStyles.backgroundColors();
        if (choices == null) {
            backgroundColor = null;
        } else {
            backgroundColor = choices[myCounter % choices.length];
        }

        String textStyle;
        choices = myStyles.textStyles();
        if (choices == null) {
            textStyle = null;
        } else {
            textStyle = choices[myCounter % choices.length];
        }

        String icon;
        choices = myStyles.icons();
        if (choices == null) {
            icon = null;
        } else {
            icon = choices[myCounter % choices.length];
        }

        ++myCounter;
        return new StyleDesc(color, backgroundColor, null, textStyle, icon);
    }
}
