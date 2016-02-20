package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of the result status of an object store operation.
 *
 * @see ObjectStore#putObjects ObjectStore.putObjects()
 * @see ObjectStore#removeObjects ObjectStore.removeObjects()
 * @see RequestResultHandler
 */
public class ResultDesc implements Encodable {
    /** Reference string of the object. */
    private String myRef;

    /** Error message, or null if no errors. */
    private String myFailure;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object acted upon.
     * @param failure  Optional error message.
     */
    @JSONMethod({ "ref", "failure" })
    public ResultDesc(String ref, OptString failure) {
        this(ref, failure.value(null));
    }

    /**
     * Direct constructor.
     *
     * @param ref  Object reference of the object that was the subject of the
     *    operation.
     * @param failure  Error message string, or null if the operation was
     *    successful.
     */
    public ResultDesc(String ref, String failure) {
        myRef = ref;
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
        JSONLiteral result = new JSONLiteral("stati", control);
        result.addParameter("ref", myRef);
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
     * Get the subject object's reference string.
     *
     * @return the object reference string of the subject object.
     */
    public String ref() {
        return myRef;
    }
}
