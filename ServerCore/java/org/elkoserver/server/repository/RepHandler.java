package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.objdb.store.GetResultHandler;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.objdb.store.ObjectStore;
import org.elkoserver.objdb.store.PutDesc;
import org.elkoserver.objdb.store.QueryDesc;
import org.elkoserver.objdb.store.UpdateDesc;
import org.elkoserver.objdb.store.RequestResultHandler;
import org.elkoserver.objdb.store.RequestDesc;
import org.elkoserver.objdb.store.ResultDesc;

/**
 * Singleton handler for the repository 'rep' protocol.
 *
 * The 'rep' protocol consists of these requests:
 *
 *   'get' - Requests the retrieval of an object (and, optionally, its
 *      contents) from the object store.
 *
 *   'put' - Requests the writing of an object into the object store.
 *
 *   'remove' - Requests the deletion of an object from the object store.
 */
class RepHandler extends BasicProtocolHandler {
    /** The repostory server proper. */
    private Repository myRepository;

    /** Local object store module. */
    private ObjectStore myObjectStore;

    /**
     * Constructor.
     */
    RepHandler(Repository repository) {
        myRepository = repository;
        myObjectStore = repository.objectStore();
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'rep'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "rep";
    }

    /**
     * Handle the 'get' verb.
     *
     * Request data retrieval.
     *
     * @param from  The connection asking for the objects.
     * @param tag  Client tag for matching replies.
     * @param what  Objects requested.
     */
    @JSONMethod({ "tag", "what" })
    public void get(final RepositoryActor from, final OptString tag,
                    RequestDesc what[])
    {
        myObjectStore.getObjects(what, new GetResultHandler() {
            public void handle(ObjectDesc results[]) {
                from.send(msgGet(RepHandler.this, tag.value(null), results));
            }
        });
    }

    /**
     * Handle the 'put' verb.
     *
     * Request data be saved in persistant storage.
     *
     * @param from  The connection asking for the write.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be written.
     */
    @JSONMethod({ "tag", "what" })
    public void put(final RepositoryActor from, final OptString tag,
                    PutDesc what[])
    {
        myObjectStore.putObjects(what, new RequestResultHandler() {
            public void handle(ResultDesc results[]) {
                from.send(msgPut(RepHandler.this, tag.value(null), results));
            }
        });
    }

    /**
     * Handle the 'update' verb.
     *
     * Request data be saved in persistant storage if it hasn't changed.
     *
     * @param from  The connection asking for the write.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be written.
     */
    @JSONMethod({ "tag", "what" })
    public void update(final RepositoryActor from, final OptString tag,
                    UpdateDesc what[])
    {
        myObjectStore.updateObjects(what, new RequestResultHandler() {
            public void handle(ResultDesc results[]) {
                from.send(msgUpdate(RepHandler.this, tag.value(null), results));
            }
        });
    }

    /**
     * Handle the 'query' verb.
     *
     * Query the database for one or more objects.
     *
     * @param from  The connection asking for the objects.
     * @param tag  Client tag for matching replies.
     * @param what  Query templates for the objects requested.
     */
    @JSONMethod({ "tag", "what" })
    public void query(final RepositoryActor from, final OptString tag,
                      QueryDesc what[])
    {
        myObjectStore.queryObjects(what, new GetResultHandler() {
            public void handle(ObjectDesc results[]) {
                from.send(msgQuery(RepHandler.this, tag.value(null), results));
            }
        });
    }

    /**
     * Handle the 'remove' verb.
     *
     * Request objects be deleted from storage.
     *
     * @param from  The connection asking for the deletions.
     * @param tag  Client tag for matching replies.
     * @param what  Objects to be deleted.
     */
    @JSONMethod({ "tag", "what" })
    public void remove(final RepositoryActor from, final OptString tag,
                       RequestDesc what[])
    {
        myObjectStore.removeObjects(what, new RequestResultHandler() {
            public void handle(ResultDesc results[]) {
               from.send(msgRemove(RepHandler.this, tag.value(null), results));
            }
        });
    }

    /**
     * Create a 'get' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Client tag for matching replies.
     * @param results  Object results.
     */
    static JSONLiteral msgGet(Referenceable target, String tag,
                              ObjectDesc results[])
    {
        JSONLiteral msg = new JSONLiteral(target, "get");
        msg.addParameterOpt("tag", tag);
        msg.addParameter("results", results);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'put' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Client tag for matching replies.
     * @param results  Status results.
     */
    static JSONLiteral msgPut(Referenceable target, String tag,
                              ResultDesc results[])
    {
        JSONLiteral msg = new JSONLiteral(target, "put");
        msg.addParameterOpt("tag", tag);
        msg.addParameter("results", results);
        msg.finish();
        return msg;
    }

    /**
     * Create an 'update' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Client tag for matching replies.
     * @param results  Status results.
     */
    static JSONLiteral msgUpdate(Referenceable target, String tag,
                                 ResultDesc results[])
    {
        JSONLiteral msg = new JSONLiteral(target, "update");
        msg.addParameterOpt("tag", tag);
        msg.addParameter("results", results);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'query' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Client tag for matching replies.
     * @param results  Object results.
     */
    static JSONLiteral msgQuery(Referenceable target, String tag,
                                ObjectDesc results[])
    {
        JSONLiteral msg = new JSONLiteral(target, "query");
        msg.addParameterOpt("tag", tag);
        msg.addParameter("results", results);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'remove' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Client tag for matching replies.
     * @param results  Status results.
     */
    static JSONLiteral msgRemove(Referenceable target, String tag,
                                 ResultDesc results[])
    {
        JSONLiteral msg = new JSONLiteral(target, "remove");
        msg.addParameterOpt("tag", tag);
        msg.addParameter("results", results);
        msg.finish();
        return msg;
    }
}
