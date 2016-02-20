package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Representation of permissible text style information in a context that can
 * contain text.
 *
 * <p>Note: this is not a mod.  StyleOptions objects are used by the {@link
 * NoteMaker} and {@link TalkOptions} mods.
 */
public class StyleOptions implements Encodable {
    /** Permissible text colors. */
    private String myColors[];

    /** Permissible background colors. */
    private String myBackgroundColors[];

    /** Permissible border colors. */
    private String myBorderColors[];

    /** Permissible text typeface/styles etc. */
    private String myTextStyles[];

    /** Permissible optional icon URLs. */
    private String myIcons[];

    /** Width of icons (in pixels). */
    private int myIconWidth;

    /** Height of icons (in pixels). */
    private int myIconHeight;

    /**
     * JSON-driven constructor.
     *
     * @param colors  Permissible foreground (text) colors.
     * @param backgroundColors  Permissible background colors.
     * @param borderColors  Permissible border colors.
     * @param textStyles  Permissible text styles.
     * @param icons  Permissible icon URLs.
     * @param iconWidth  Common width of icons, or -1 if not relevant.
     * @param iconHeight  Common height of icons, or -1 if not relevant.
     */
    @JSONMethod({ "colors", "backgroundColors", "borderColors", "textStyles",
                  "icons", "iconWidth", "iconHeight" })
    public StyleOptions(String colors[], String backgroundColors[],
                        String borderColors[], String textStyles[],
                        String icons[], OptInteger iconWidth,
                        OptInteger iconHeight) {
        myColors = colors;
        myBackgroundColors = backgroundColors;
        myBorderColors = borderColors;
        myTextStyles = textStyles;
        myIcons = icons;
        myIconWidth = iconWidth.value(-1);
        myIconHeight = iconHeight.value(-1);
    }

    /**
     * Test if a particular string is a member of an array of allowed choices.
     *
     * @param choice  The string to test.
     * @param choices  The array to check it against.
     *
     * @return true if 'choice' is in 'choices'.
     */
    private boolean allowedChoice(String choice, String choices[]) {
        if (choices == null || choices.length == 0) {
            return choice == null;
        } else {
            for (String test : choices) {
                if (choice.equals(test)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Test if a particular {@link StyleDesc} is permissible according to this
     * object's settings.
     *
     * @param style  The {@link StyleDesc} to test.
     *
     * @return true if 'style' is acceptable to this object, false if not.
     */
    public boolean allowedStyle(StyleDesc style) {
        return
            allowedChoice(style.color(), myColors) &&
            allowedChoice(style.backgroundColor(), myBackgroundColors) &&
            allowedChoice(style.borderColor(), myBorderColors) &&
            allowedChoice(style.textStyle(), myTextStyles) &&
            allowedChoice(style.icon(), myIcons);
    }

    /**
     * Get the permissible background colors.
     *
     * @return an array of the permissible background colors.
     */
    public String[] backgroundColors() {
        return myBackgroundColors;
    }

    /**
     * Get the permissible border colors.
     *
     * @return an array of the permissible border colors.
     */
    public String[] borderColors() {
        return myBorderColors;
    }

    /**
     * Get the permissible foreground (text) colors.
     *
     * @return an array of the permissible foreground colors.
     */
    public String[] colors() {
        return myColors;
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
        JSONLiteral result = new JSONLiteral("styleoptions", control);
        if (myColors != null && myColors.length > 0) {
            result.addParameter("colors", myColors);
        }
        if (myBackgroundColors != null && myBackgroundColors.length > 0) {
            result.addParameter("backgroundColors", myBackgroundColors);
        }
        if (myBorderColors != null && myBorderColors.length > 0) {
            result.addParameter("borderColors", myBorderColors);
        }
        if (myTextStyles != null && myTextStyles.length > 0) {
            result.addParameter("textStyles", myTextStyles);
        }
        if (myIcons != null && myIcons.length > 0) {
            result.addParameter("icons", myIcons);
        }
        if (myIconWidth >= 0) {
            result.addParameter("iconWidth", myIconWidth);
        }
        if (myIconHeight >= 0) {
            result.addParameter("iconHeight", myIconHeight);
        }
        result.finish();
        return result;
    }

    /**
     * Extract a choice from an array of choices.
     *
     * @param choice  The choice to extract, or null if the default is sought
     * @param choices   Array of allowed choices.
     *
     * @return 'choice' if 'choice' is not null, else the default if 'choice'
     *    selects the default by being null.
     */
    private String extract(String choice, String choices[]) {
        if (choice == null) {
            if (choices == null || choices.length == 0) {
                return null;
            } else {
                return choices[0];
            }
        } else {
            return choice;
        }
    }

    /**
     * Get the permissible icon URLs.
     *
     * @return an array of the permissible icon URLs.
     */
    public String[] icons() {
        return myIcons;
    }

    /**
     * Get the height of the icons.
     *
     * @return the (common) height of the icons, or -1 if they do not have a
     *    common height.
     */
    public int iconHeight() {
        return myIconHeight;
    }

    /**
     * Get the width of the icons.
     *
     * @return the (common) width of the icons, or -1 if they do not have a
     *    common width.
     */
    public int iconWidth() {
        return myIconWidth;
    }

    /**
     * Produce a new {@link StyleDesc} object given another, partially
     * specified, {@link StyleDesc} object.
     *
     * @param style  The {@link StyleDesc} to start from.
     *
     * @return a new {@link StyleDesc} object that is a copy of 'style' with
     *    additional attributes according to the defaults contained in this
     *    object, or null if one of the attributes specified by 'style' is not
     *    permitted by this object's settings.
     */
    public StyleDesc mergeStyle(StyleDesc style) {
        String backgroundColor;
        String borderColor;
        String color;
        String icon;
        String textStyle;

        if (style == null) {
            backgroundColor = extract(null, myBackgroundColors);
            borderColor = extract(null, myBorderColors);
            color = extract(null, myColors);
            icon = extract(null, myIcons);
            textStyle = extract(null, myTextStyles);
        } else {
            backgroundColor = extract(style.backgroundColor(),
                                      myBackgroundColors);
            borderColor = extract(style.borderColor(), myBorderColors);
            color = extract(style.color(), myColors);
            icon = extract(style.icon(), myIcons);
            textStyle = extract(style.textStyle(), myTextStyles);
        }
        StyleDesc result = new StyleDesc(color, backgroundColor, borderColor,
                                         textStyle, icon);
        if (allowedStyle(result)) {
            return result;
        } else {
            return null;
        }
    }

    /**
     * Get the permissible text styles.
     *
     * @return an array of the permissible text styles.
     */
    public String[] textStyles() {
        return myTextStyles;
    }
}
