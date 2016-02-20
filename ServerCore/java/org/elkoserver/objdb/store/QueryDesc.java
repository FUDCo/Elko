package org.elkoserver.objdb.store;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;

/**
 * Description of a query for an object.
 *
 * @see ObjectStore#queryObjects ObjectStore.queryObjects()
 */
public class QueryDesc implements Encodable {
    /** Query template */
    private JSONObject myTemplate;

    /** Collection to query */
    private String myCollectionName;

    /** Result limit */
    private int myMaxResults;

    /**
     * JSON-driven (and direct) constructor.
     *
     * @param template  Query template indicating the objects queried.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit (the default if omitted).
     */
    @JSONMethod({ "template", "coll", "limit" })
    public QueryDesc(JSONObject template, OptString collectionName,
                     OptInteger maxResults) {
        this(template, collectionName.value(null), maxResults.value(0));
    }

    /**
     * Direct constructor.
     *
     * @param template  Query template indicating the objects queried.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     */
    public QueryDesc(JSONObject template, String collectionName,
                     int maxResults) {
        myTemplate = template;
        myCollectionName = collectionName;
        myMaxResults = maxResults;
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
        JSONLiteral result = new JSONLiteral("queryi", control);
        result.addParameter("template", myTemplate);
        result.addParameterOpt("coll", myCollectionName);
        if (myMaxResults > 0) {
            result.addParameter("limit", myMaxResults);
        }
        result.finish();
        return result;
    }

    /**
     * Get the query template for the queried object(s).
     *
     * @return the query template for the query.
     */
    public JSONObject template() {
        return myTemplate;
    }

    /**
     * Get the collection for this query.
     *
     * @return the name of collection to query, or null to take the default.
     */
    public String collectionName() {
        return myCollectionName;
    }

    /**
     * Get the result limit for this query.
     *
     * @return the maximum number of results for this query.
     */
    public int maxResults() {
        return myMaxResults;
    }
}
