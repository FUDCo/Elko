package org.elkoserver.objdb.store;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.util.trace.Trace;

/**
 * Access to a persistent object data storage mechanism.
 *
 * <p>This interface is used by both the Repository (which provides object
 * storage remotely via a JSON protocol) and {@link
 * org.elkoserver.objdb.ObjDBLocal} (which provides object storage locally via
 * direct access to an object database).  In either case, they are configured
 * with the fully qualified class name of an implementor of this interface,
 * which they instantiate at startup time.
 */
public interface ObjectStore {
    /**
     * Do whatever initialization is required to begin serving objects.  This
     * method gets invoked once, at server startup time.
     *
     * @param props  Properties describing configuration information.
     * @param propRoot  Prefix string for selecting relevant properties.
     * @param trace  Trace object for use in logging.
     */
    void initialize(BootProperties props, String propRoot, Trace trace);

    /**
     * Service a 'get' request.  This is a request to retrieve one or more
     * objects from the store.
     *
     * @param what  The objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    void getObjects(RequestDesc what[], GetResultHandler handler);

    /**
     * Service a 'query' request.  This is a request to query one or more
     * objects from the store.
     *
     * @param what  Query templates for the objects sought.
     * @param handler  Object to receive results (i.e., the objects retrieved
     *    or failure indicators), when available.
     */
    void queryObjects(QueryDesc what[], GetResultHandler handler);

    /**
     * Service a 'put' request.  This is a request to write one or more objects
     * to the store.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    void putObjects(PutDesc what[], RequestResultHandler handler);

    /**
     * Service a 'remove' request.  This is a request to delete one or more
     * objects from the store.
     *
     * @param what  The objects to be removed.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    void removeObjects(RequestDesc what[], RequestResultHandler handler);

    /**
     * Do any work required immediately prior to shutting down the server.
     * This method gets invoked at most once, at server shutdown time.
     */
    void shutdown();

    /**
     * Service an 'update' request.  This is a request to write one or more
     * objects to the store, subject to a version number check to assure
     * atomicity.
     *
     * @param what  The objects to be written.
     * @param handler  Object to receive results (i.e., operation success or
     *    failure indicators), when available.
     */
    void updateObjects(UpdateDesc what[], RequestResultHandler handler);
}
