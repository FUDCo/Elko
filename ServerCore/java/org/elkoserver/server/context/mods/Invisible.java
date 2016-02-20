package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.BasicObject;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;

/**
 * Marker mod to indicate that an item should be hidden from clients.  This mod
 * only makes sense when attached to items, not to contexts or users.
 *
 * If this mod is attached to an item, that item and its contents will be
 * omitted from the description of the containing context that is transmitted
 * to users who enter that context.
 */
public class Invisible extends Mod implements ObjectCompletionWatcher, ItemMod
{
    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    Invisible() {
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
            JSONLiteral result = new JSONLiteral("invisible", control);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Mark the item as invisible, now that there's an item to mark.
     *
     * <p>Application code should not call this method.
     */
    public void objectIsComplete() {
        object().setVisibility(BasicObject.VIS_NONE);
    }
}
