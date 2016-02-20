package org.elkoserver.server.context;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Object stored in the object database that holds a list of static objects
 * that should be loaded at server startup time.  Each entry in the list is
 * actually a pair: a key string, by which the object will be known at runtime
 * in the server, and a ref string, by which the object is stored in the
 * object database.
 */
class StaticObjectList {
    /** Collection of descriptors mapping class tags to Java class names. */
    private StaticObjectListElem myStatics[];

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "statics" })
    StaticObjectList(StaticObjectListElem statics[]) {
        myStatics = statics;
    }

    /**
     * Fetch the static objects from the object database.
     *
     * @param odb  The object database to tell.
     * @param contextor  Contextor for whom these objects are being loaded
     * @param tr  Trace object for logging errors
     */
    void fetchFromODB(ObjDB odb, Contextor contextor, Trace appTrace) {
        for (StaticObjectListElem elem : myStatics) {
            odb.getObject(elem.ref, null,
                          new StaticObjectReceiver(contextor, elem, appTrace));
        }
    }

    private class StaticObjectReceiver implements ArgRunnable {
        StaticObjectListElem myElem;
        Contextor myContextor;
        Trace tr;
        StaticObjectReceiver(Contextor contextor, StaticObjectListElem elem,
                             Trace appTrace)
        {
            myContextor = contextor;
            myElem = elem;
            tr = appTrace;
        }
        public void run(Object obj) {
            if (obj != null) {
                tr.eventi("loading static object '" + myElem.ref + "' as '" +
                          myElem.key + "'");
                myContextor.addStaticObject(myElem.key, obj);
            } else {
                tr.errori("unable to load static object '" + myElem.ref + "'");
            }
        }
    }

    static private class StaticObjectListElem {
        final String key;
        final String ref;
        
        @JSONMethod({ "key", "ref" })
            StaticObjectListElem(String key, String ref) {
            this.key = key;
            this.ref = ref;
        }
    }
}
