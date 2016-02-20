package org.elkoserver.server.context;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Object representing the persistent information about the server state as a
 * whole that doesn't otherwise have an object to go in.
 */
class ServerDesc implements Encodable {
    /** Next item ID to allocate. */
    private int myNextID;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "nextid" })
    ServerDesc(int nextID) {
        myNextID = nextID;
    }

    /**
     * Encode this server description for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral literal = new JSONLiteral("serverdesc", control);
        literal.addParameter("nextid", myNextID);
        literal.finish();
        return literal;
    }

    /**
     * Get the next available item ID.
     *
     * @return the next item ID.
     */
    String nextItemID() {
        return "item-" + (myNextID++);
    }
}
