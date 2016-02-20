package org.elkoserver.objdb;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.util.trace.Trace;

/**
 * Object stored in the object database that keeps track of the mapping between
 * a class tag (as used in the object database) and the actual Java class that
 * it corresponds to.
 */
class ClassTagDesc {
    /** Tag string by which a class is encoded in the object database. */
    private String myTag;

    /** Java classname by which the class is known to the JVM. */
    private String myClassName;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "tag", "name" })
    ClassTagDesc(String tag, String className) {
        myTag = tag;
        myClassName = className;
    }

    /**
     * Tell an object database that about the class this object describes.
     *
     * @param odb  The object database to tell.
     * @param tr  Trace object for error logging.
     */
    void useInODB(ObjDB odb, Trace tr) {
        try {
            odb.addClass(myTag, Class.forName(myClassName));
        } catch (ClassNotFoundException e) {
            tr.errorm("unable to load class info for '" + myTag + "': class " +
                      e.getMessage() + " not found");
        }
    }
}
