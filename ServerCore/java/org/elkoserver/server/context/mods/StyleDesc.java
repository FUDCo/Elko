package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Representation of style information for something containing text.
 *
 * <p>Note: this is not a mod.  StyleDesc objects are used by the {@link Note},
 * {@link NoteMaker}, {@link TalkPrefs} and {@link TalkOptions} mods and by the
 * {@link StyleOptions} object.
 */
public class StyleDesc implements Encodable {
    /** Text color. */
    private String myColor;

    /** Background color. */
    private String myBackgroundColor;

    /** Border color. */
    private String myBorderColor;

    /** Text typeface/style etc. */
    private String myTextStyle;

    /** Optional icon URL. */
    private String myIcon;

    /**
     * JSON-driven constructor.
     *
     * @param color  Optional foreground (text) color.
     * @param backgroundColor  Optional background color.
     * @param borderColor  Optional border color.
     * @param textStyle  Optional style string (e.g., "bold", "italic", etc.)
     *    for text.
     * @param icon  Optional URL of an icon to go with text.
     */
    @JSONMethod({ "color", "backgroundColor", "borderColor", "textStyle",
                  "icon" })
    public StyleDesc(OptString color, OptString backgroundColor,
                     OptString borderColor, OptString textStyle,
                     OptString icon)
    {
        this(color.value(null), backgroundColor.value(null),
             borderColor.value(null), textStyle.value(null), icon.value(null));
    }

    /**
     * Direct constructor.
     *
     * @param color  Foreground (text) color, or null if none.
     * @param backgroundColor  Background color, or null if none.
     * @param borderColor  Border color, or null if none.
     * @param textStyle Style string for text (e.g, "bold", "italic", etc.), or
     *    null if none.
     * @param icon URL of an icon to go with the text, or null if none.
     */
    public StyleDesc(String color, String backgroundColor, String borderColor,
                     String textStyle, String icon)
    {
        myColor = color;
        myBackgroundColor = backgroundColor;
        myBorderColor = borderColor;
        myTextStyle = textStyle;
        myIcon = icon;
    }

    /**
     * Get the background color.
     *
     * @return this style's background color, or null if there is none.
     */
    public String backgroundColor() {
        return myBackgroundColor;
    }

    /**
     * Get the border color.
     *
     * @return this style's border color, or null if there is none.
     */
    public String borderColor() {
        return myBorderColor;
    }

    /**
     * Get the foreground (text) color.
     *
     * @return this style's foreground color, or null if there is none.
     */
    public String color() {
        return myColor;
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("style", control);
        result.addParameterOpt("color", myColor);
        result.addParameterOpt("backgroundColor", myBackgroundColor);
        result.addParameterOpt("borderColor", myBorderColor);
        result.addParameterOpt("textStyle", myTextStyle);
        result.addParameterOpt("icon", myIcon);
        result.finish();
        return result;
    }

    /**
     * Get the icon URL.
     *
     * @return this style's icon URL, or null if there is none.
     */
    public String icon() {
        return myIcon;
    }

    /**
     * Merge this StyleDesc with another, partially specified StyleDesc,
     * creating a new StyleDesc.
     *
     * @param partial  The (partial) StyleDesc to merge with
     *
     * @return a new StyleDesc with the settings of 'partial' where 'partial'
     *    specifies them, and the settings of this object where 'partial' does
     *    not specify them.
     */
    public StyleDesc mergeStyle(StyleDesc partial) {
        return new StyleDesc(overlay(partial.color(), myColor),
                             overlay(partial.backgroundColor(),
                                     myBackgroundColor),
                             overlay(partial.borderColor(), myBorderColor),
                             overlay(partial.textStyle(), myTextStyle),
                             overlay(partial.icon(), myIcon));
    }

    /**
     * Overlay a new value on an old one.
     *
     * @param newChoice  The new value.
     * @param oldChoice  The old value.
     *
     * @return 'oldChoice' if 'newChoice' is null, else 'newChoice'.
     */
    private String overlay(String newChoice, String oldChoice) {
        return newChoice != null ? newChoice : oldChoice;
    }

    /**
     * Get the text style for this StyleDesc.  This is a string that specifies
     * attributes such as typeface, bold, italic, etc.
     *
     * @return this style's text style string, or null if there is none.
     */
    public String textStyle() {
        return myTextStyle;
    }
}
