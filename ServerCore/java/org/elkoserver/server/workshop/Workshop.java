package org.elkoserver.server.workshop;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Workshop Server.
 */
public class Workshop extends RefTable {
    /** Server object. */
    private Server myServer;

    /** Database that persistent objects are stored in. */
    private ObjDB myODB;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Flag that is set once server shutdown begins. */
    private boolean amShuttingDown;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Workshop(Server server, Trace appTrace) {
        this(server.openObjectDatabase("conf.workshop"), server, appTrace);
    }

    /**
     * Internal constructor.
     *
     * This constructor exists only because of a Java limitation: it needs to
     * create the object database object and then both save it in an instance
     * variable AND pass it to the superclass constructor.  However, Java
     * requires that the first statement in a constructor MUST be a call to
     * the superclass constructor or to another constructor of the same class.
     * It is possible to create the database and pass it to the superclass
     * constructor or to save it in an instance variable, but not both.  To get
     * around this, the public constructor creates the database object in a
     * parameter expression in a call to this internal constructor, which will
     * then possess it in a parameter variable whence it can be both passed to
     * the superclass constructor and saved in an instance variable.
     *
     * @param odb  Database for persistent object storage.
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    private Workshop(ObjDB odb, Server server, Trace appTrace) {
        super(odb);
        tr = appTrace;

        if (odb == null) {
            tr.fatalError("no database specified");
        }
        myODB = odb;

        myServer = server;
        odb.addClass("auth", AuthDesc.class);
        addRef(new ClientHandler(this));
        addRef(new AdminHandler(this));

        amShuttingDown = false;
        server.registerShutdownWatcher(new ShutdownWatcher() {
            public void noteShutdown() {
                amShuttingDown = true;
                myODB.shutdown();
            }
        });
    }

    /**
     * Add a worker to the object table.
     *
     * @param key  Name by which this object will be known within the server
     * @param obj  The object itself
     */
    void addWorkerObject(String key, WorkerObject worker) {
        worker.activate(key, this);
    }

    /**
     * Obtain the application trace object for the workshop.
     *
     * @return the workshop's trace object.
     */
    public Trace appTrace() {
        return tr;
    }

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    boolean isShuttingDown() {
        return amShuttingDown;
    }

    /**
     * Load the statically configured worker objects.
     */
    void loadStartupWorkers() {
        myODB.addClass("workers", StartupWorkerList.class);
        myODB.getObject("workers", null,
                        new StartupWorkerListReceiver("workers"));

        String workerListRefs =
            myServer.props().getProperty("conf.workshop.workers");
        if (workerListRefs != null) {
            StringTokenizer tags = new StringTokenizer(workerListRefs, " ,;:");
            while (tags.hasMoreTokens()) {
                String tag = tags.nextToken();
                myODB.getObject(tag, null, new StartupWorkerListReceiver(tag));
            }
        }
    }

    private class StartupWorkerListReceiver implements ArgRunnable {
        String myTag;
        StartupWorkerListReceiver(String tag) {
            myTag = tag;
        }
        public void run(Object obj) {
            StartupWorkerList workers = (StartupWorkerList) obj;
            if (workers != null) {
                tr.eventi("loading startup worker list '" + myTag + "'");
                workers.fetchFromODB(myODB, Workshop.this, tr);
            } else {
                tr.errori("unable to load startup worker list '" + myTag +
                          "'");
            }
        }
    }

    /**
     * Register a newly loaded workshop service with the broker.
     *
     * @param serviceName  The name of the service to register
     */
    void registerService(String serviceName) {
        List<ServiceDesc> services = myServer.services();
        List<ServiceDesc> newServices = new LinkedList<ServiceDesc>();
        for (ServiceDesc service : services) {
            if ("workshop-service".equals(service.service())) {
                newServices.add(service.subService(serviceName));
            }
        }
        for (ServiceDesc service : newServices) {
            myServer.registerService(service);
        }
    }

    /**
     * Reinitialize the server.
     */
    void reinit() {
        myServer.reinit();
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    void shutdown(boolean kill) {
        myServer.shutdown(kill);
    }

    /**
     * Fetch an object from the repository.
     *
     * @param ref  The ref of the object desired.
     * @param handler  Callback that will be invoked with the object in
     *   question, or null if the object was not available.
     */
    public void getObject(String ref, ArgRunnable handler) {
        myODB.getObject(ref, null, handler);
    }

    /**
     * Fetch an object from a particular collection in the repository.
     *
     * @param ref  The ref of the object desired.
     * @param collection  The name of the collection to use.
     * @param handler  Callback that will be invoked with the object in
     *   question, or null if the object was not available.
     */
    public void getObject(String ref, String collection, ArgRunnable handler) {
        myODB.getObject(ref, collection, handler);
    }

    /**
     * Query the repository.
     *
     * @param query  JSON object containing a MongoDB query structure.
     * @param maxResults  Maximum number of result objects acceptable; a value
     *    of 0 means no limit.
     * @param handler  Callback that will be invoked with a results array, or
     *    null if the query failed.
     */
    public void queryObjects(JSONObject query, int maxResults,
                             ArgRunnable handler)
    {
        myODB.queryObjects(query, null, maxResults, handler);
    }

    /**
     * Query a particular collection in the repository.
     *
     * @param query  JSON object containing a MongoDB query structure.
     * @param collection  The collection to use.
     * @param maxResults  Maximum number of result objects acceptable; a value
     *    of 0 means no limit.
     * @param handler  Callback that will be invoked with a results array, or
     *    null if the query failed.
     */
    public void queryObjects(JSONObject query, String collection,
                             int maxResults, ArgRunnable handler)
    {
        myODB.queryObjects(query, collection, maxResults, handler);
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     */
    public void putObject(String ref, Encodable object) {
        putObject(ref, object, null);
    }

    /**
     * Store an object into the repository with results notification.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     * @param resultHandler  Handler that wil be invoked with the result of
     *   the operation; the result will be null if the operation suceeded, or
     *   an error string if the operation failed.
     */
    public void putObject(String ref, Encodable object,
                          ArgRunnable resultHandler)
    {
        myODB.putObject(ref, object, null, false, resultHandler);
    }

    /**
     * Store an object into a particular collection in the repository with
     * results notification.
     *
     * @param ref  Ref of the object to write.
     * @param object  The object itself.
     * @param collection  The name of the collection to use.
     * @param resultHandler  Handler that wil be invoked with the result of
     *   the operation; the result will be null if the operation suceeded, or
     *   an error string if the operation failed.
     */
    public void putObject(String ref, Encodable object, String collection,
                          ArgRunnable resultHandler)
    {
        myODB.putObject(ref, object, collection, false, resultHandler);
    }

    /**
     * Update the state of an object in the repository.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param resultHandler  Handler that wil be invoked with the result of
     *   the operation; the result will be null if the operation suceeded, or
     *   an error string if the operation failed.
     */
    public void updateObject(String ref, int version, Encodable object,
                             ArgRunnable resultHandler)
    {
        myODB.updateObject(ref, version, object, null, resultHandler);
    }

    /**
     * Update the state of an object in some collection in the repository.
     *
     * @param ref  Ref of the object to write.
     * @param version  Version number of the instance being replaced
     * @param object  The object itself.
     * @param collection  The collection to use.
     * @param resultHandler  Handler that wil be invoked with the result of
     *   the operation; the result will be null if the operation suceeded, or
     *   an error string if the operation failed.
     */
    public void updateObject(String ref, int version, Encodable object,
                             String collection, ArgRunnable resultHandler)
    {
        myODB.updateObject(ref, version, object, collection, resultHandler);
    }

    /**
     * Delete an object from the repository.
     *
     * @param ref  The ref of the object to be deleted.
     */
    public void removeObject(String ref) {
        myODB.removeObject(ref, null, null);
    }
}
