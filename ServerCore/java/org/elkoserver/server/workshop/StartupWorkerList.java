package org.elkoserver.server.workshop;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Object stored in the object database that holds a list of worker objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the worker object will be known at
 * runtime in the server, and a ref string, by which the object is stored in
 * the object database.  By convention these two are the same but they need not
 * be.
 */
class StartupWorkerList {
    /** Collection of descriptors mapping class tags to Java class names. */
    private WorkerListElem myWorkers[];

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "workers" })
    StartupWorkerList(WorkerListElem workers[]) {
        myWorkers = workers;
    }

    /**
     * Fetch the worker objects from the object database.
     *
     * @param odb  The object database to tell.
     * @param workshop  Workshop for whom these objects are being loaded
     * @param tr  Trace object for logging errors
     */
    void fetchFromODB(ObjDB odb, Workshop workshop, Trace appTrace) {
        for (WorkerListElem elem : myWorkers) {
            odb.getObject(elem.ref, null,
                          new WorkerReceiver(workshop, elem, appTrace));
        }
    }

    private class WorkerReceiver implements ArgRunnable {
        WorkerListElem myElem;
        Workshop myWorkshop;
        Trace tr;
        WorkerReceiver(Workshop workshop, WorkerListElem elem, Trace appTrace)
        {
            myWorkshop = workshop;
            myElem = elem;
            tr = appTrace;
        }
        public void run(Object obj) {
            if (obj != null) {
                if (obj instanceof WorkerObject) {
                    tr.eventi("loading worker object '" + myElem.ref +
                              "' as '" + myElem.key + "'");
                    myWorkshop.addWorkerObject(myElem.key, (WorkerObject) obj);
                } else {
                    tr.errori("alleged worker object '" + myElem.ref +
                              "' is not actually a WorkerObject, ignoring it");
                }
            } else {
                tr.errori("unable to load worker object '" + myElem.ref + "'");
            }
        }
    }

    static private class WorkerListElem {
        final String key;
        final String ref;
        
        @JSONMethod({ "key", "ref" })
            WorkerListElem(String key, String ref) {
            this.key = key;
            this.ref = ref;
        }
    }
}
