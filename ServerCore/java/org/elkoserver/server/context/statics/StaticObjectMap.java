package org.elkoserver.server.context.statics;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple extendsion of HashMap (mapping String->JSONObject) so it can be
 * loaded as a static object from the object store.
 */
public class StaticObjectMap extends HashMap<String, JSONObject> {
    /**
     * JSON-driven constructor.
     *
     * @param map  JSON object will be interpreted as a mapping from String to
     *    JSONObject.
     */
    @JSONMethod({ "map" })
    StaticObjectMap(JSONObject map) {
        for (Map.Entry<String, Object> entry : map.properties()) {
            put(entry.getKey(), (JSONObject) entry.getValue());
        }
    }
}
