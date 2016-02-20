package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.GeneralMod;
import org.elkoserver.server.context.Mod;

/**
 * Mod to associate an image with an object.  This mod may be attached to a
 * context or a user or an item.
 *
 * This mod has no behavioral repertoire of its own, but simply holds onto
 * descriptive information for the benefit of the client.
 */
public class Image extends Mod implements GeneralMod {
    /* Persistent state, initialized from database. */

    /** Width, in pixels. */
    private OptInteger myWidth;

    /** Height, in pixels. */
    private OptInteger myHeight;
    
    /** Image URL. */
    private String myImg;

    /**
     * JSON-driven constructor.
     *
     * @param width  Horizontal extent of the image (optional).
     * @param height  Vertical extent of the image (optional).
     * @param img  URL of the image itself.
     */
    @JSONMethod({ "width", "height", "img" })
    public Image(OptInteger width, OptInteger height, String img) {
        myWidth  = width;
        myHeight = height;
        myImg    = img;
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
        JSONLiteral result = new JSONLiteral("image", control);
        if (myWidth.present()) {
            result.addParameter("width", myWidth.value());
        }
        if (myHeight.present()) {
            result.addParameter("height", myHeight.value());
        }
        result.addParameter("img", myImg);
        result.finish();
        return result;
    }
}
