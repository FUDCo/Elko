package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of the result status of an object update operation.
 *
 * @see ObjectStore#updateObjects ObjectStore.updateObjects()
 * @see RequestResultHandler
 */
public class UpdateResultDesc extends ResultDesc {
    /** Flag indicating status of atomic update. */
    private boolean amAtomicFailure;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Object reference of the object acted upon.
     * @param failure  Optional error message.
     */
    @JSONMethod({ "ref", "failure", "atomic" })
    public UpdateResultDesc(String ref, OptString failure,
                            boolean isAtomicFailure)
    {
        this(ref, failure.value(null), isAtomicFailure);
    }

    /**
     * Direct constructor.
     *
     * @param ref  Object reference of the object that was the subject of the
     *    operation.
     * @param failure  Error message string, or null if the operation was
     *    successful.
     * @param isAtomicFailure  Flag that is true if operation would have
     *    completed but doing so would have violated atomicity.
     */
    public UpdateResultDesc(String ref, String failure,
                            boolean isAtomicFailure) 
    {
        super(ref, failure);
        amAtomicFailure = isAtomicFailure;
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
        JSONLiteral result = new JSONLiteral("ustati", control);
        result.addParameter("ref", ref());
        result.addParameterOpt("failure", failure());
        result.addParameter("atomic", amAtomicFailure);
        result.finish();
        return result;
    }

    /**
     * Get the atomic failure flag.
     *
     * @return the atomic failure flag for this operatio.
     */
    public boolean isAtomicFailure() {
        return amAtomicFailure;
    }
}
