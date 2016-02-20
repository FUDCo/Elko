package org.elkoserver.objdb;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.elkoserver.foundation.json.ObjectDecoder;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Parser;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Base class for both local and remote concrete implementations of the ObjDB
 * interface.
 */
abstract class ObjDBBase implements ObjDB {
    /** Table mapping JSON object type tags to Java classes. */
    private Map<String, Class<?>> myClasses;

    /** Application trace object for logging. */
    Trace tr;

    /**
     * Constructor.
     */
    ObjDBBase(Trace appTrace) {
        tr = appTrace;
        myClasses = new HashMap<String, Class<?>>();
    }

    /**
     * Inform the object database about a mapping from a JSON object type tag
     * string to a Java class.
     *
     * @param tag  The JSON object type tag string.
     * @param type  The class that 'tag' labels.
     */
    public void addClass(String tag, Class<?> type) {
        myClasses.put(tag, type);
    }

    /**
     * Convert a parsed JSON object description into the object it describes.
     *
     * @param jsonObj  The object being decoded.
     *
     * @return the object described by 'jsonObj'.
     */
    protected Object decodeJSONObject(JSONObject jsonObj) {
        Object result = null;
        String typeTag = jsonObj.type();
        if (typeTag != null) {
            Class<?> type = myClasses.get(typeTag);
            if (type != null) {
                result = ObjectDecoder.decode(type, jsonObj, this);
            } else {
                tr.errorm("no class for type tag '" + typeTag + "'");
            }
        }
        return result;
    }

    /**
     * Decode a collection of JSON strings into an object.
     *
     * @param ref  Reference string for the object to be decoded.
     * @param results  A collection of object descriptors; one of these will be
     *    the object named by 'ref', others will be that object's contents.
     *
     * @return the decoded object, or null if it could not be decoded according
     *    to the specified parameters.
     */
    Object decodeObject(String ref, ObjectDesc results[]) {
        String objStr = null;
        for (ObjectDesc result : results) {
            if (ref.equals(result.ref())) {
                objStr = result.obj();
                break;
            }
        }
        if (objStr == null) {
            tr.errorm("no object retrieved from ODB for ref " + ref);
            return null;
        } else {
            try {
                Parser parser = new Parser(objStr);
                JSONObject jsonObj = parser.parseObjectLiteral();
                insertContents(jsonObj, results);
                //jsonObj.addProperty("ref", ref);
                return decodeJSONObject(jsonObj);
            } catch (SyntaxError e) {
                tr.errorm("object store syntax error getting " + ref + ": " +
                          e.getMessage());
                return null;
            }
        }
    }

    /**
     * Given an object reference, obtain the object(s) it refers to, from among
     * the collection of objects retreived by the store.  If the reference
     * value is a string, the output is the object referenced by that string.
     * If the reference value is a JSON array of strings, the output value is
     * an array of the objects referenced by those strings.  Otherwise, the
     * result is null.
     *
     * In the case of an array result, if all of the elments are of a common
     * type, then the result will be an array of that type.  Otherwise, the
     * result will be an array of Object.
     *
     * @param refValue  The value(s) of the property before dereferencing.
     * @param objs  The objects returned by the store.
     *
     * @return  The value(s) of the property after dereferencing.
     */
    private Object dereferenceValue(Object refValue, ObjectDesc objs[]) {
        Object result = null;
        if (refValue instanceof JSONArray) {
            Iterator<Object> refs = ((JSONArray) refValue).iterator();
            Object contents[] = new Object[((JSONArray) refValue).size()];
            Class<?> resultClass = null;
            for (int i = 0; i < contents.length; ++i) {
                Object ref = refs.next();
                if (ref instanceof String) {
                    contents[i] = decodeObject((String) ref, objs);
                    if (contents[i] != null) {
                        Class<?> elemClass = contents[i].getClass();
                        if (resultClass == null) {
                            resultClass = elemClass;
                        } else if (elemClass.isAssignableFrom(resultClass)) {
                            resultClass = elemClass;
                        } else if (!resultClass.isAssignableFrom(elemClass)) {
                            resultClass = Object.class;
                        }
                    }
                } else {
                    contents[i] = null;
                }
            }
            result = contents;
            if (resultClass != Object.class) {
                result = Array.newInstance(resultClass, contents.length);
                System.arraycopy(contents, 0, result, 0, contents.length);
            }
        } else if (refValue instanceof String) {
            result = decodeObject((String) refValue, objs);
        }
        return result;
    }

    /**
     * Replace the properties of a JSONObject that describe the object's
     * contents with the contents objects themselves, as retrieved by the
     * store.
     *
     * Any property whose name begins with "ref$" is treated as an object
     * reference.  It is removed from the object and replaced with a new
     * property whose name has the "ref$" prefix stripped off and whose value
     * is the object or objects referenced.
     *
     * @param obj  The JSONObject whose contents are to be inserted.
     * @param results  The results returned by the store.
     */
    private void insertContents(JSONObject obj, ObjectDesc results[]) {
        List<Map.Entry<String, Object>> contentsProps = null;
        Iterator<Map.Entry<String, Object>> iter =
            obj.properties().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Object> entry = iter.next();
            String propName = entry.getKey();
            if (propName.startsWith("ref$")) {
                iter.remove();
                if (contentsProps == null) {
                    contentsProps =
                        new LinkedList<Map.Entry<String, Object>>();
                }
                contentsProps.add(entry);
            }
        }
        if (contentsProps != null) {
            for (Map.Entry<String, Object> entry : contentsProps) {
                Object prop = dereferenceValue(entry.getValue(), results);
                if (prop != null) {
                    obj.addProperty(entry.getKey().substring(4), prop);
                }
            }
        }
    }

    /**
     * Load one or more named class descriptor objects.
     *
     * @param classDescRefs  A comma separated list of class descriptor object
     *    names.
     */
    void loadClassDesc(String classDescRefs) {
        addClass("classes", ClassDesc.class);
        addClass("class", ClassTagDesc.class);
        getObject("classes", null, new ClassDescReceiver("classes"));
        if (classDescRefs != null) {
            StringTokenizer tags = new StringTokenizer(classDescRefs, " ,;:");
            while (tags.hasMoreTokens()) {
                String tag = tags.nextToken();
                getObject(tag, null, new ClassDescReceiver(tag));
            }
        }
    }

    private class ClassDescReceiver implements ArgRunnable {
        String myTag;
        ClassDescReceiver(String tag) {
            myTag = tag;
        }
        public void run(Object obj) {
            ClassDesc classes = (ClassDesc) obj;
            if (classes != null) {
                tr.eventi("loading classDesc '" + myTag + "'");
                classes.useInODB(ObjDBBase.this, tr);
            } else {
                tr.errorm("unable to load classDesc '" + myTag + "'");
            }
        }
    }

    /**
     * Get the class associated with a given JSON type tag string.
     *
     * @param baseType  Base class from which result class must be derived.
     * @param typeName  JSON type tag identifying the desired class.
     *
     * @return a class named by 'typeName' suitable for assignment to a
     *    method or constructor parameter of class 'baseType'.
     */
    public Class<?> resolveType(Class<?> baseType, String typeName) {
        Class<?> result = myClasses.get(typeName);
        if (result != null && !baseType.isAssignableFrom(result)) {
            result = null;
        }
        return result;
    }
}
