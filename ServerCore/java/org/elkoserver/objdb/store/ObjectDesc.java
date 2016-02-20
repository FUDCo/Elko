package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of a requested object returned from the object store.
 *
 * @see ObjectStore#getObjects ObjectStore.getObjects()
 * @see GetResultHandler
 */
public class ObjectDesc implements Encodable {
    /** Reference string of the object. */
    private String myRef;

    /** Object description string, or null if there was an error. */
    private String myObj;

    /** Error message, or null if no errors. */
    private String myFailure;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object requested.
     * @param obj  Optional object description.
     * @param failure  Optional error message.
     */
    @JSONMethod({ "ref", "obj", "failure" })
    public ObjectDesc(String ref, OptString obj, OptString failure) {
        this(ref, obj.value(null), failure.value(null));
    }

    /**
     * Direct constructor.
     *
     * @param ref  Object reference of the object requested.
     * @param obj Object description (a JSON string describing the object, if
     *    the object was retrieved, or null if retrieval failed).
     * @param failure  Error message string if retrieval failed, or null if
     *    retrieval succeeded.
     */
    public ObjectDesc(String ref, String obj, String failure) {
        myRef = ref;
        myObj = obj;
        myFailure = failure;
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
        JSONLiteral result = new JSONLiteral("obji", control);
        result.addParameter("ref", myRef);
        result.addParameterOpt("obj", myObj);
        result.addParameterOpt("failure", myFailure);
        result.finish();
        return result;
    }

    /**
     * Get the error message string.
     *
     * @return the error message string, or null if there is none (i.e., if
     *    this represents a success result).
     */
    public String failure() {
        return myFailure;
    }

    /**
     * Get the requested object's description.
     *
     * @return the requested object's description (a JSON string), or null if
     *    there is no object (i.e., if this represents an error result).
     */
    public String obj() {
        return myObj;
    }

    /**
     * Get the requested object's reference string.
     *
     * @return the object reference string of the requested object.
     */
    public String ref() {
        return myRef;
    }
}
