package org.elkoserver.server.gatekeeper.passwd;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Database object describing a place name to context mapping.
 */
class PlaceDesc implements Encodable {
    /** The place name. */
    private String myName;

    /** The context it maps to. */
    private String myContextID;

    /**
     * Normal and JSON-driven constructor.
     *
     * @param name  The place name.
     * @param contextID  Identifier of the context that 'name' maps to.
     */
    @JSONMethod({ "name", "context" })
    PlaceDesc(String name, String contextID) {
        myName = name;
        myContextID = contextID;
    }

    /**
     * Obtain this place's context identifier.
     *
     * @return this place's context identifier string.
     */
    String contextID() {
        return myContextID;
    }

    /**
     * Encode this place for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("place", control);
        result.addParameter("name", myName);
        result.addParameter("context", myContextID);
        result.finish();
        return result;
    }

    /**
     * Obtain this place's name.
     *
     * @return this place's name string.
     */
    String name() {
        return myName;
    }
}
