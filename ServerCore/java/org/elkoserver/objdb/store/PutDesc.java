package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of a request write to the object store.
 *
 * @see ObjectStore#putObjects ObjectStore.putObjects()
 */
public class PutDesc implements Encodable {
    /** Reference string of the object. */
    private String myRef;

    /** Object description string. */
    private String myObj;

    /** Name of collection to write to. */
    private String myCollectionName;

    /** Flag governing whether object put must be unprecedented. */
    private boolean amRequireNew;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object to write.
     * @param obj  Object description.
     * @param collectionName  Name of collection to write to, or omit to take
     *    the configured default.
     * @param requireNew  Optional flag to force failure if object with ref
     *    already exists.
     */
    @JSONMethod({ "ref", "obj", "coll", "requirenew" })
    public PutDesc(String ref, String obj, OptString collectionName,
                   OptBoolean requireNew)
    {
        this(ref, obj, collectionName.value(null), requireNew.value(false));
    }

    /**
     * Direct constructor.
     *
     * @param ref  Object reference for the object.
     * @param obj Object description (a JSON string describing the object).
     * @param collectionName  Name of collection to write to, or null to take
     *    the configured default.
     * @param requireNew  If true and an object with the given ref already
     *     exists, the write fails.
     */
    public PutDesc(String ref, String obj, String collectionName,
                   boolean requireNew)
    {
        myRef = ref;
        myObj = obj;
        myCollectionName = collectionName;
        amRequireNew = requireNew;
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
        JSONLiteral result = new JSONLiteral("puti", control);
        result.addParameter("ref", myRef);
        result.addParameterOpt("obj", myObj);
        result.addParameterOpt("coll", myCollectionName);
        if (amRequireNew) {
            result.addParameter("requirenew", amRequireNew);
        }
        result.finish();
        return result;
    }

    /**
     * Get the collection name.
     *
     * @return the collection name to write to, or null to indicate the default
     */
    public String collectionName() {
        return myCollectionName;
    }

    /**
     * Get the object's description.
     *
     * @return the object's description (a JSON string).
     */
    public String obj() {
        return myObj;
    }

    /**
     * Get the object's reference string.
     *
     * @return the object reference string of the object to write.
     */
    public String ref() {
        return myRef;
    }

    /**
     * Test if this write must be to a new object.
     *
     * @return true if this write must be to a new object.
     */
    public boolean isRequireNew() {
        return amRequireNew;
    }
}
