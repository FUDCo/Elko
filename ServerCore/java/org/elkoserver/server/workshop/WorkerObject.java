package org.elkoserver.server.workshop;

import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

/**
 * Base class for all worker objects.
 */
abstract public class WorkerObject
    implements DispatchTarget, Encodable, Referenceable
{
    /** Reference string for this object. */
    private String myRef;

    /** Service name to register for this worker, or null for none. */
    private String myServiceName;

    /** The workshop for this server. */
    private Workshop myWorkshop;

    /**
     * Constructor.
     *
     * @param serviceName  Service name to register with broker, or null for
     *    none.
     */
    public WorkerObject(String serviceName) {
        myServiceName = serviceName;
    }

    /**
     * Make this object live inside the workshop server.
     *
     * @param ref  Reference string identifying this object.
     * @param workshop  The workshop for this server.
     */
    void activate(String ref, Workshop workshop) {
        myRef = ref;
        myWorkshop = workshop;
        if (ref != null) {
            workshop.addRef(this);
        }
        if (myServiceName != null) {
            workshop.registerService(myServiceName);
        }
        activate();
    }

    /**
     * Overridable hook for subclasses to be notified about activation.
     */
    public void activate() {
    }

    /**
     * Obtain the workshop for this server.
     *
     * @return the Workshop object for this server.
     */
    protected Workshop workshop() {
        return myWorkshop;
    }

    /* ----- Encodable interface ------------------------------------------ */

    /**
     * Produce a {@link JSONLiteral} representing the encoded state of this
     * object, suitable for transmission over a messaging medium or for writing
     * to persistent storage.  The default implementation makes this object
     * not actually encodable, but subclasses can override.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a {@link JSONLiteral} representing the encoded state of this
     *    object.
     */
    public JSONLiteral encode(EncodeControl control) {
        return null;
    }

    /* ----- Referenceable interface --------------------------------------- */

    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     *    messages, either as the message target or as a parameter value.
     */
    public String ref() {
        return myRef;
    }
}
