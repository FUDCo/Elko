package org.elkoserver.server.repository;

import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.StaticTypeResolver;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.objdb.store.GetResultHandler;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.objdb.store.ObjectStore;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Repository.
 */
class Repository {
    /** Table for mapping object references in messages. */
    RefTable myRefTable;

    /** Server object. */
    private Server myServer;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Local object storage module. */
    private ObjectStore myObjectStore;

    /** Number of repository clients currently connected. */
    private int myRepClientCount;

    /** Flag that is set once server shutdown begins. */
    private boolean amShuttingDown;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Repository(Server server, Trace appTrace) {
        myServer = server;
        tr = appTrace;

        myRefTable = new RefTable(StaticTypeResolver.theStaticTypeResolver);
        myRefTable.addRef(new RepHandler(this));
        myRefTable.addRef(new AdminHandler(this));

        amShuttingDown = false;
        myRepClientCount = 0;
        server.registerShutdownWatcher(new ShutdownWatcher() {
                public void noteShutdown() {
                    amShuttingDown = true;
                    countRepClients(0);
                }
            });

        String propRoot = "conf.rep";
        String objectStoreClassName =
            server.props().getProperty(propRoot + ".objstore",
               "org.elkoserver.objdb.store.filestore.FileObjectStore");
        Class objectStoreClass = null;
        try {
            objectStoreClass = Class.forName(objectStoreClassName);
        } catch (ClassNotFoundException e) {
            tr.fatalError("object store class " + objectStoreClassName +
                          " not found");
        }
        try {
            myObjectStore = (ObjectStore) objectStoreClass.newInstance();
        } catch (IllegalAccessException e) {
            tr.fatalError("unable to access object store constructor: " + e);
        } catch (InstantiationException e) {
            tr.fatalError("unable to instantiate object store object: " + e);
        }
        myObjectStore.initialize(myServer.props(), propRoot, tr);
    }

    /**
     * Add to or subtract from the number of repository clients connected.  If
     * the number drops to zero and this server is in the midst of shutting
     * down, terminate the object store.
     *
     * @param delta  The amount to change the count by.
     */
    void countRepClients(int delta) {
        myRepClientCount += delta;
        if (amShuttingDown && myRepClientCount <= 0) {
            myObjectStore.shutdown();
        }
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
     * Get the object store currently in use.
     *
     * @return the object storage object for this server.
     */
    ObjectStore objectStore() {
        return myObjectStore;
    }

    /**
     * Get the ref table.
     *
     * @return the object ref table that resolves object reference strings for
     *    messages sent to this server.
     */
    RefTable refTable() {
        return myRefTable;
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
}
