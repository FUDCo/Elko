package org.elkoserver.server.context.caps;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.context.ContextKey;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.UserMod;

/**
 * Capability to enable entry to one or more entry controlled contexts.
 */
public class ContextKeyCap
    extends Cap
    implements ItemMod, UserMod, ContextKey
{

    /** Reference strings for contexts this capability enables entry to. */
    private String myContexts[];

    /**
     * JSON-driven constructor.
     *
     * @param contexts  Array of refs for the contexts to which this key grants
     *    entry permission.
     */
    @JSONMethod({ "contexts" })
    public ContextKeyCap(JSONObject raw, String contexts[])
        throws JSONDecodingException
    {
        super(raw);
        myContexts = contexts;
    }

    /**
     * Test if this capability enables entry to a particular context.
     *
     * @param contextRef  Reference string of the context of interest.
     *
     * @return true if this capability enables entry to the context designated
     *    by 'contextRef', false if not.
     */
    public boolean enablesEntry(String contextRef) {
        if (isExpired()) {
            return false;
        }
        for (String context : myContexts) {
            if (context.equals(contextRef)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("ctxkey", control);
        encodeDefaultParameters(result);
        result.addParameter("contexts", myContexts);
        result.finish();
        return result;
    }
}
