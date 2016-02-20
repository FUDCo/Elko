package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of a request to update to the object store.
 *
 * @see ObjectStore#updateObjects ObjectStore.updateObjects()
 */
public class UpdateDesc extends PutDesc {
    /** Expected version number of object being updated. */
    private int myVersion;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object to write.
     * @param version  Object version being updated
     * @param obj  Object description.
     * @param collectionName  Name of collection to write to, or null to take
     *    the configured default.
     */
    @JSONMethod({ "ref", "version", "obj", "coll" })
    public UpdateDesc(String ref, int version, String obj,
                      OptString collectionName)
    {
        this(ref, version, obj, collectionName.value(null));
    }

    /**
     * Direct constructor.
     *
     * @param ref  Object reference for the object.
     * @param version  Object version being updated
     * @param obj Object description (a JSON string describing the object).
     * @param collectionName  Name of collection to write to, or null to take
     *    the configured default.
     */
    public UpdateDesc(String ref, int version, String obj,
                      String collectionName)
    {
        super(ref, obj, collectionName, false);
        myVersion = version;
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
        JSONLiteral result = new JSONLiteral("updatei", control);
        result.addParameter("ref", ref());
        result.addParameter("version", myVersion);
        result.addParameterOpt("obj", obj());
        result.addParameterOpt("coll", collectionName());
        result.finish();
        return result;
    }

    /**
     * Get the version number.
     *
     * @return the version number of the version being updated.
     */
    public int version() {
        return myVersion;
    }
}
