package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;

/**
 * Description of a request for an object.
 *
 * @see ObjectStore#getObjects ObjectStore.getObjects()
 */
public class RequestDesc implements Encodable {
    /** Object requested. */
    private String myRef;

    /** Flag controlling contents retrieval. */
    private boolean myContents;

    /** Name of collection to fetch from. */
    private String myCollectionName;

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string identifying the object requested.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default.
     * @param contents If true, retrieve the referenced object and any objects
     *    it contains; if false (the default if omitted), only retrieve the
     *    referenced object itself.
     */
    @JSONMethod({ "ref", "coll", "contents" })
    public RequestDesc(String ref, OptString collectionName,
                       OptBoolean contents) {
        this(ref, collectionName.value(null), contents.value(false));
    }

    /**
     * Direct constructor.
     *
     * @param ref  Reference string identifying the object requested.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default.
     * @param contents If true, retrieve the referenced object and any objects
     *    it contains; if false, only retrieve the referenced object itself.
     */
    public RequestDesc(String ref, String collectionName, boolean contents) {
        myRef = ref;
        myCollectionName = collectionName;
        myContents = contents;
    }

    /**
     * Get the name of the collection being queried, or null if it's to be the
     * default.
     *
     * @return the value of this request's collection name.
     */
    public String collectionName() {
        return myCollectionName;
    }

    /**
     * Get the value of the contents flag.
     *
     * @return the value of this request's contents flag.
     */
    public boolean contents() {
        return myContents;
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
        JSONLiteral result = new JSONLiteral("reqi", control);
        result.addParameter("ref", myRef);
        if (myContents) {
            result.addParameter("contents", myContents);
        }
        result.finish();
        return result;
    }

    /**
     * Get the reference string of the requested object.
     *
     * @return the reference string of the requested object.
     */
    public String ref() {
        return myRef;
    }
}
