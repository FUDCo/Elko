package org.elkoserver.objdb.store.mongostore;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Parser;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.objdb.store.GetResultHandler;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.objdb.store.ObjectStore;
import org.elkoserver.objdb.store.PutDesc;
import org.elkoserver.objdb.store.QueryDesc;
import org.elkoserver.objdb.store.RequestDesc;
import org.elkoserver.objdb.store.UpdateDesc;
import org.elkoserver.objdb.store.RequestResultHandler;
import org.elkoserver.objdb.store.ResultDesc;
import org.elkoserver.objdb.store.UpdateResultDesc;
import org.elkoserver.util.trace.Trace;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import org.bson.types.ObjectId;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An {@link ObjectStore} implementation that stores objects in a MongoDB NoSQL
 * object database.
 */
public class MongoObjectStore implements ObjectStore {
    /** Trace object for diagnostics. */
    private Trace tr;

    /** The MongoDB instance in which the objects are stored. */
    private Mongo myMongo;

    /** The Mongo database we are using */
    private DB myDB;

    /** The default Mongo collection holding the normal objects */
    private DBCollection myODBCollection;

    /**
     * Constructor.  Currently there is nothing to do, since all the real
     * initialization work happens in {@link #initialize initialize()}.
     */
    public MongoObjectStore() {
    }

    /**
     * Do the initialization required to begin providing object store
     * services.
     *
     * <p>The property <tt>"<i>propRoot</i>.odb.mongo.hostport"</tt> should
     * specify the address of the MongoDB server holding the objects.
     *
     * <p>The optional property <tt>"<i>propRoot</i>.odb.mongo.dbname"</tt>
     * allows the Mongo database name to be specified.  If omitted, this
     * defaults to <tt>"elko"</tt>.
     *
     * <p>The optional property <tt>"<i>propRoot</i>.odb.mongo.collname"</tt>
     * allows the collection containing the object repository to be specified.
     * If omitted, this defaults to <tt>"odb"</tt>.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     * @param appTrace  Trace object for use in logging.
     */
    public void initialize(BootProperties props, String propRoot,
                           Trace appTrace)
    {
        tr = appTrace;
        propRoot = propRoot + ".odb.mongo";

        String addressStr = props.getProperty(propRoot + ".hostport");
        if (addressStr == null) {
            tr.fatalError("no mongo database server address specified");
        }
        int colon = addressStr.indexOf(':');
        int port;
        String host;
        if (colon < 0) {
            port = 27017;
            host = addressStr;
        } else {
            port = Integer.parseInt(addressStr.substring(colon + 1)) ;
            host = addressStr.substring(0, colon);
        }
        //try {
            myMongo = new Mongo(host, port);
        //} catch (UnknownHostException e) {
        //    tr.fatalError("mongodb server " + addressStr + ": unknown host");
        //}
        String dbName = props.getProperty(propRoot + ".dbname", "elko");
        myDB = myMongo.getDB(dbName);

        String collName = props.getProperty(propRoot + ".collname", "odb");
        myODBCollection = myDB.getCollection(collName);
    }

    /**
     * Obtain the object or objects that a field value references.
     *
     * @param value  The value to dereference.
     * @param collection   The collection to fetch from.
     * @param results  List in which to place the object or objects obtained.
     */
    private void dereferenceValue(Object value, DBCollection collection,
                                  List<ObjectDesc> results) {
        if (value instanceof JSONArray) {
            for (Object elem : (JSONArray) value) {
                if (elem instanceof String) {
                    results.addAll(doGet((String) elem, collection));
                }
            }
        } else if (value instanceof String) {
            results.addAll(doGet((String) value, collection));
        }
    }

    /**
     * Perform a single 'get' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be gotten.
     * @param collection  Collection to get from.
     *
     * @return a list of ObjectDesc objects, the first of which will be
     *    the result of getting 'ref' and the remainder, if any, will be the
     *    results of getting any contents objects.
     */
    private List<ObjectDesc> doGet(String ref, DBCollection collection) {
        List<ObjectDesc> results = new LinkedList<ObjectDesc>();

        String failure = null;
        String obj = null;
        List<ObjectDesc> contents = null;
        try {
            DBObject query = new BasicDBObject();
            query.put("ref", ref);
            DBObject dbObj = collection.findOne(query);
            if (dbObj != null) {
                JSONObject jsonObj = dbObjectToJSONObject(dbObj);
                obj = jsonObj.sendableString();
                contents = doGetContents(jsonObj, collection);
            } else {
                failure = "not found";
            }
        } catch (Exception e) {
            obj = null;
            failure = e.getMessage();
        }

        results.add(new ObjectDesc(ref, obj, failure));
        if (contents != null) {
            results.addAll(contents);
        }
        return results;
    }

    private JSONObject dbObjectToJSONObject(DBObject dbObj) {
        JSONObject result = new JSONObject();
        for (String key : dbObj.keySet()) {
            if (!key.startsWith("_")) {
                Object value = dbObj.get(key);
                if (value instanceof BasicDBList) {
                    value = dbListToJSONArray((BasicDBList) value);
                } else if (value instanceof DBObject) {
                    value = dbObjectToJSONObject((DBObject) value);
                }
                result.addProperty(key, value);
            } else if (key.equals("_id")) {
                ObjectId oid = (ObjectId) dbObj.get(key);
                result.addProperty(key, oid.toString());
            }
        }
        return result;
    }

    private JSONArray dbListToJSONArray(BasicDBList dbList) {
        JSONArray result = new JSONArray();
        for (Object elem : dbList) {
            if (elem instanceof BasicDBList) {
                elem = dbListToJSONArray((BasicDBList) elem);
            } else if (elem instanceof DBObject) {
                elem = dbObjectToJSONObject((DBObject) elem);
            }
            result.add(elem);
        }
        return result;
    }

    private DBObject jsonLiteralToDBObject(String objStr, String ref) {
        JSONObject obj;
        try {
            obj = JSONObject.parse(objStr);
        } catch (SyntaxError e) {
            return null;
        }
        DBObject result = jsonObjectToDBObject(obj);
        result.put("ref", ref);

        // WARNING: the following is a rather profound and obnoxious modularity
        // boundary violation, but as ugly as it is, it appears to be the least
        // bad way to accomodate some of the limitations of Mongodb's
        // geo-indexing feature.  In order to spatially index an object,
        // Mongodb requires the 2D coordinate information to be stored in a
        // 2-element object or array property at the top level of the object to
        // be indexed. In the case of a 2-element object, the order the
        // properties appear in the JSON encoding is meaningful, which totally
        // violates the definition of JSON but that's what they did.
        // Unfortunately, the rest of our object encoding/decoding
        // infrastructure requires object-valued properties whose values are
        // polymorphic classes to contain a "type" property to indicate what
        // class they are.  Since there's no way to control the order in which
        // properties will be encoded when the object is serialized to JSON, we
        // risk having Mongodb mistake the type tag for the latitude or
        // longitude.  Even if we could overcome this, we'd still risk having
        // Mongodb mix the latitude and longitude up with each other.
        // Consequently, what we do is notice if an object being written has a
        // "pos" property of type "geopos", and if so we manually generate an
        // additional "_qpos_" property that is well formed according to
        // Mondodb's 2D coordinate encoding rules, and have Mongodb index
        // *that*.  When an object is read from the database, we strip this
        // property off again before we return the object to the application.

        try {
            JSONObject pos = obj.optObject("pos", null);
            if (pos != null) {
                String type = pos.optString("type", null);
                if ("geopos".equals(type)) {
                    double lat = pos.optDouble("lat", 0.0);
                    double lon = pos.optDouble("lon", 0.0);
                    DBObject qpos = new BasicDBObject();
                    qpos.put("lat", lat);
                    qpos.put("lon", lon);
                    result.put("_qpos_", qpos);
                }
            }
        } catch (JSONDecodingException e) {
            // this can't actually happen
        }
        // End of ugly modularity boundary violation

        return result;
    }

    private Object valueToDBValue(Object value) {
        if (value instanceof JSONObject) {
            value = jsonObjectToDBObject((JSONObject) value);
        } else if (value instanceof JSONArray) {
            value = jsonArrayToDBArray((JSONArray) value);
        } else if (value instanceof Long) {
            long intValue = ((Long) value).longValue();
            if (Integer.MIN_VALUE <= intValue &&
                intValue <= Integer.MAX_VALUE) {
                value = new Integer((int) intValue);
            }
        }
        return value;
    }

    private ArrayList<Object> jsonArrayToDBArray(JSONArray arr) {
        ArrayList<Object> result = new ArrayList<Object>(arr.size());
        for (Object elem : arr) {
            result.add(valueToDBValue(elem));
        }
        return result;
    }

    private DBObject jsonObjectToDBObject(JSONObject obj) {
        DBObject result = new BasicDBObject();
        for (Map.Entry<String, Object> prop : obj.properties()) {
            result.put(prop.getKey(), valueToDBValue(prop.getValue()));
        }
        return result;
    }

    /**
     * Fetch the contents of an object.
     *
     * @param obj  The object whose contents are sought.
     *
     * @return a List of ObjectDesc objects for the contents as
     *    requested.
     */
    private List<ObjectDesc> doGetContents(JSONObject obj,
                                           DBCollection collection) {
        List<ObjectDesc> results = new LinkedList<ObjectDesc>();
        for (Map.Entry<String, Object> entry : obj.properties()) {
            String propName = entry.getKey();
            if (propName.startsWith("ref$")) {
                dereferenceValue(entry.getValue(), collection, results);
            }
        }
        return results;
    }

    /**
     * Perform a single 'put' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param obj  JSON string encoding the object to be written.
     * @param collection  Collection to put to.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doPut(String ref, String obj, DBCollection collection,
                             boolean requireNew)
    {
        String failure = null;
        if (obj == null) {
            failure = "no object data given";
        } else {
            try {
                DBObject objectToWrite = jsonLiteralToDBObject(obj, ref);
                if (requireNew) {
                    WriteResult wr = collection.insert(objectToWrite);
                } else {
                    DBObject query = new BasicDBObject();
                    query.put("ref", ref);
                    collection.update(query, objectToWrite, true, false);
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
        }
        return new ResultDesc(ref, failure);
    }

    /**
     * Perform a single 'update' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be written.
     * @param version  Expected version number of object before updating.
     * @param obj  JSON string encoding the object to be written.
     * @param collection  Collection to put to.
     *
     * @return an UpdateResultDesc object describing the success or failure of
     *    the operation.
     */
    private UpdateResultDesc doUpdate(String ref, int version, String obj,
                                      DBCollection collection)
    {
        String failure = null;
        boolean atomicFailure = false;
        if (obj == null) {
            failure = "no object data given";
        } else {
            try {
                DBObject objectToWrite = jsonLiteralToDBObject(obj, ref);
                DBObject query = new BasicDBObject();
                query.put("ref", ref);
                query.put("version", version);
                WriteResult result =
                    collection.update(query, objectToWrite, false, false);
                if (result.getN() != 1) {
                    failure = "stale version number on update";
                    atomicFailure = true;
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
        }
        return new UpdateResultDesc(ref, failure, atomicFailure);
    }

    /**
     * Perform a single 'remove' operation on the local object store.
     *
     * @param ref  Object reference string of the object to be deleted.
     * @param collection  Collection to remove from.
     *
     * @return a ResultDesc object describing the success or failure of the
     *    operation.
     */
    private ResultDesc doRemove(String ref, DBCollection collection) {
        String failure = null;
        try {
            DBObject query = new BasicDBObject();
            query.put("ref", ref);
            collection.remove(query);
        } catch (Exception e) {
            failure = e.getMessage();
        }
        return new ResultDesc(ref, failure);
    }

    /**
     * Service a 'get' request.  This is a request to retrieve one or more
     * objects from the object store.
     *
     * @param what  The objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    public void getObjects(RequestDesc what[], GetResultHandler handler) {
        List<ObjectDesc> resultList = new LinkedList<ObjectDesc>();
        for (RequestDesc req : what) {
            resultList.addAll(doGet(req.ref(),
                                    getCollection(req.collectionName())));
        }
        ObjectDesc results[] = new ObjectDesc[resultList.size()];
        results = (ObjectDesc[]) resultList.toArray(results);

        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the object store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void putObjects(PutDesc what[], RequestResultHandler handler) {
        ResultDesc results[] = new ResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            DBCollection collection = getCollection(what[i].collectionName());
            results[i] = doPut(what[i].ref(), what[i].obj(), collection,
                               what[i].isRequireNew());
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Service an 'update' request.  This is a request to write one or more
     * objects to the store, subject to a version number check to assure
     * atomicity.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void updateObjects(UpdateDesc what[],
                              RequestResultHandler handler)
    {
        UpdateResultDesc results[] = new UpdateResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            DBCollection collection = getCollection(what[i].collectionName());
            results[i] = doUpdate(what[i].ref(), what[i].version(),
                                  what[i].obj(), collection);
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Perform a single 'query' operation on the local object store.
     *
     * @param template  Query template indicating what objects are sought.
     * @param collection  Collection to query.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     *
     * @return a list of ObjectDesc objects for objects matching the query.
     */
    private List<ObjectDesc> doQuery(JSONObject template,
                                     DBCollection collection, int maxResults) {
        List<ObjectDesc> results = new LinkedList<ObjectDesc>();

        try {
            DBObject query = jsonObjectToDBObject(template);
            DBCursor cursor;
            if (maxResults > 0) {
                cursor = collection.find(query, null, 0, -maxResults);
            } else {
                cursor = collection.find(query);
            }
            for (DBObject dbObj : cursor) {
                JSONObject jsonObj = dbObjectToJSONObject(dbObj);
                String obj = jsonObj.sendableString();
                results.add(new ObjectDesc("query", obj, null));
            }
        } catch (Exception e) {
            results.add(new ObjectDesc("query", null, e.getMessage()));
        }
        return results;
    }

    /**
     * Map from a collection name to a Mongo collection object.
     *
     * @param collectionName  Name of the collection desired, or null to get
     *    the configured default (whatever that may be).
     *
     * @return the DBCollection object corresponding to collectionName.
     */
    private DBCollection getCollection(String collectionName) {
        if (collectionName == null) {
            return myODBCollection;
        } else {
            return myDB.getCollection(collectionName);
        }
    }

    /**
     * Service a 'query' request.  This is a request to query one or more
     * objects from the store.
     *
     * @param what  Query templates for the objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    public void queryObjects(QueryDesc what[], GetResultHandler handler) {
        List<ObjectDesc> resultList = new LinkedList<ObjectDesc>();
        for (QueryDesc req : what) {
            DBCollection collection = getCollection(req.collectionName());
            resultList.addAll(doQuery(req.template(), collection,
                                      req.maxResults()));
        }
        ObjectDesc results[] = new ObjectDesc[resultList.size()];
        results = (ObjectDesc[]) resultList.toArray(results);

        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Service a 'remove' request.  This is a request to delete one or more
     * objects from the object store.
     *
     * @param what  The objects to be removed.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    public void removeObjects(RequestDesc what[],
                              RequestResultHandler handler) {
        ResultDesc results[] = new ResultDesc[what.length];
        for (int i = 0; i < what.length; ++i) {
            results[i] = doRemove(what[i].ref(),
                                  getCollection(what[i].collectionName()));
        }
        if (handler != null) {
            handler.handle(results);
        }
    }

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    public void shutdown() {
        /* nothing to do in this implementation */
    }
}
