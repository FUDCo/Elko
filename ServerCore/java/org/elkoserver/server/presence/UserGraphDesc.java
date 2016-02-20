package org.elkoserver.server.presence;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * A user's social graph, as represented in the ODB.
 */
class UserGraphDesc implements Encodable {
    /** The user's ref */
    final String ref;

    /** An array of the refs of all the users the user is connected to. */
    final String friends[];

    /**
     * JSON-driven constructor.
     *
     * @param ref  The user's reference string
     * @param friends  Array of the user's friend references
     */
    @JSONMethod({ "ref", "friends" })
    UserGraphDesc(String ref, String friends[]) {
        this.ref = ref;
        this.friends = friends;
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
        JSONLiteral result = new JSONLiteral("ugraf", control);
        result.addParameter("ref", ref);
        result.addParameter("friends", friends);
        result.finish();
        return result;
    }
}
