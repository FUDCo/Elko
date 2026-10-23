package org.elkoserver.objdb.store.mongostore.test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.util.UUID;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.store.*;
import org.elkoserver.objdb.store.mongostore.MongoObjectStore;
import org.elkoserver.util.trace.Trace;

/** Standalone regression using a running loopback MongoDB and a disposable DB. */
public class MongoObjectStoreTest {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static ResultDesc put(MongoObjectStore store, String ref,
                                  String obj, boolean create) {
        final ResultDesc[][] result = new ResultDesc[1][];
        store.putObjects(new PutDesc[] { new PutDesc(ref, obj, (String) null, create) },
                         rows -> result[0] = rows);
        require(result[0] != null && result[0].length == 1, "Missing put result");
        return result[0][0];
    }

    private static UpdateResultDesc update(MongoObjectStore store, int version,
                                           String obj) {
        final ResultDesc[][] result = new ResultDesc[1][];
        store.updateObjects(new UpdateDesc[] {
            new UpdateDesc("probe", version, obj, (String) null)
        }, rows -> result[0] = rows);
        require(result[0] != null && result[0].length == 1, "Missing update result");
        return (UpdateResultDesc) result[0][0];
    }

    private static ObjectDesc get(MongoObjectStore store) {
        final ObjectDesc[][] result = new ObjectDesc[1][];
        store.getObjects(new RequestDesc[] {
            new RequestDesc("probe", (String) null, false)
        }, rows -> result[0] = rows);
        require(result[0] != null && result[0].length == 1, "Missing get result");
        return result[0][0];
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected loopback MongoDB port");
        }
        int port = Integer.parseInt(args[0]);
        String dbName = "elko_test_" + UUID.randomUUID().toString().replace("-", "");
        MongoObjectStore store = new MongoObjectStore();
        MongoObjectStore compatible = new MongoObjectStore();
        try (MongoClient client = new MongoClient("127.0.0.1", port)) {
            DB db = client.getDB(dbName);
            try {
                db.command(new BasicDBObject("ping", 1));
                DBCollection collection = db.getCollection("objects");
                collection.createIndex(new BasicDBObject("ref", 1),
                                       new BasicDBObject("unique", true));
                BootProperties props = new BootProperties();
                props.setProperty("test.odb.mongo.hostport", "127.0.0.1:" + port);
                props.setProperty("test.odb.mongo.dbname", dbName);
                props.setProperty("test.odb.mongo.collname", "objects");
                store.initialize(props, "test", Trace.trace("mongo-test"));
                String first = "{\"ref\":\"probe\",\"version\":1,\"closed\":true}";
                require(put(store, "probe", first, true).failure() == null,
                        "Create failed");
                require(put(store, "probe", first, true).failure() != null,
                        "Duplicate create was accepted");
                require(JSONObject.parse(get(store).obj()).getInt("version") == 1,
                        "Read failed");
                String next = "{\"ref\":\"probe\",\"version\":2}";
                require(update(store, 1, next).failure() == null, "Update failed");
                JSONObject fetched = JSONObject.parse(get(store).obj());
                require(fetched.getInt("version") == 2 &&
                        fetched.getProperty("closed") == null,
                        "Update did not replace the whole document");
                require(update(store, 1, first).isAtomicFailure(),
                        "Stale update was accepted");
                require(JSONObject.parse(get(store).obj()).getInt("version") == 2,
                        "Stale update changed stored data");
                require(put(store, "probe", first, false).failure() == null,
                        "Put failed");
                require(put(store, "probe", next, false).failure() == null,
                        "Replacing put failed");
                require(JSONObject.parse(get(store).obj()).getProperty("closed") == null,
                        "Put did not replace the whole document");
                for (int i = 0; i < 110; ++i) {
                    require(put(store, "row" + i, "{\"type\":\"querytest\"}", true)
                            .failure() == null, "Query fixture create failed");
                }
                for (int limit : new int[] { 1, 105, 0 }) {
                    final ObjectDesc[][] result = new ObjectDesc[1][];
                    store.queryObjects(new QueryDesc[] {
                        new QueryDesc(JSONObject.parse("{\"type\":\"querytest\"}"),
                                      (String) null, limit)
                    }, rows -> result[0] = rows);
                    require(result[0].length == (limit == 0 ? 110 : limit),
                            "Incorrect query limit " + limit);
                    for (ObjectDesc row : result[0]) {
                        require(row.failure() == null && row.obj() != null,
                                "Query failed");
                    }
                }
                final ResultDesc[][] removed = new ResultDesc[1][];
                store.removeObjects(new RequestDesc[] {
                    new RequestDesc("probe", (String) null, false)
                }, rows -> removed[0] = rows);
                require(removed[0][0].failure() == null, "Remove failed");
                require("not found".equals(get(store).failure()), "Remove retained object");
                String metadata = "{\"ref\":\"probe\",\"version\":1," +
                    "\"_applicationMetadata\":{\"owner\":null,\"token\":\"test\"},\"_private\":42}";
                require(put(store, "probe", metadata, true).failure() == null,
                        "Metadata fixture failed");
                require(JSONObject.parse(get(store).obj()).getProperty("_applicationMetadata") == null,
                        "Default underscore filtering changed");
                props.setProperty("test.odb.mongo.preservefields", "_applicationMetadata");
                props.setProperty("test.odb.mongo.legacyversionzero", "true");
                compatible.initialize(props, "test", Trace.trace("mongo-compatible-test"));
                JSONObject preserved = JSONObject.parse(get(compatible).obj());
                require("test".equals(preserved.getObject("_applicationMetadata").getString("token")),
                        "Selected metadata field was lost");
                require(preserved.getObject("_applicationMetadata").sendableString().contains("\"owner\":null"),
                        "Explicit null metadata value was lost");
                require(preserved.getProperty("_private") == null,
                        "Unselected underscore field was exposed");
                collection.update(new BasicDBObject("ref", "probe"),
                                  new BasicDBObject("ref", "probe"));
                require(update(store, 0, first).isAtomicFailure(),
                        "Default missing-version behavior changed");
                require(update(compatible, 0, first).failure() == null,
                        "Opt-in legacy version zero failed");
                require(update(compatible, 0, first).isAtomicFailure(),
                        "Stale zero-version write was accepted");
                collection.update(new BasicDBObject("ref", "probe"),
                    new BasicDBObject("ref", "probe").append("version", null));
                require(update(compatible, 0, first).isAtomicFailure(),
                        "Malformed null version was treated as absent");
                System.out.println("PASS: Mongo create/read/replace, duplicate and stale " +
                                   "write rejection, query limits, remove, opt-in metadata and legacy versions");
            } finally {
                store.shutdown();
                compatible.shutdown();
                db.dropDatabase();
            }
        }
    }
}
