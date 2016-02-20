package org.elkoserver.objdb;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.util.trace.Trace;

/**
 * Object stored in the object database that keeps track of the mapping between
 * all the class tags known to the object database and the actual Java classes
 * that they correspond to.
 */
class ClassDesc {
    /** Collection of descriptors mapping class tags to Java class names. */
    private ClassTagDesc myClasses[];

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "classes" })
    ClassDesc(ClassTagDesc classes[]) {
        myClasses = classes;
    }

    /**
     * Tell an object database about all the classes this object describes.
     *
     * @param odb  The object database to tell.
     * @param tr  Trace object for error logging.
     */
    void useInODB(ObjDB odb, Trace tr) {
        for (ClassTagDesc odbClass : myClasses) {
            odbClass.useInODB(odb, tr);
        }
    }
}
