package org.elkoserver.foundation.json;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Parser;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.util.trace.Trace;

/**
 * A producer of some class of Java objects from JSON-encoded object
 * descriptors.
 */
public class ObjectDecoder {
    /** Mapping from Java class to the specific decoder for that class.  This
        is a cache of decoders, to avoid recomputing reflection information. */
    private static Map<Class, ObjectDecoder> theDecoders =
        new HashMap<Class, ObjectDecoder>();

    /** Reflection information for the Java constructor this decoder invokes.*/
    private ConstructorInvoker myConstructor;

    /**
     * Constructor.
     *
     * Extract the appropriate constructor and associated descriptive
     * information from a class using the Java reflection API.
     *
     * The class must have a JSON-driven constructor.  Such constructors are
     * marked with the {@link JSONMethod} annotation.  The value of the
     * annotation is an array of Strings containing as many strings as the
     * constructor has parameters.  These strings will be the names of JSON
     * object descriptor properties and will be mapped one-to-one to the
     * corresponding parameters of the constructor itself.  Note that when the
     * constructor is invoked, any unmapped parameters (that is, properties
     * that exist in the JSON descriptor for the object but whose names do not
     * correspond to names in the JSONMethod annotation array) will be ignored.
     *
     * Alternatively, the constructor may have (exactly) one more parameter
     * than the number of Strings in the {@link JSONMethod} annotation, in
     * which case the one additional parameter must be of type {@link
     * JSONObject} and it must be the first parameter.  This first parameter
     * will be passed the value of the uninterpreted JSON object from which the
     * (other) parameters were extracted.
     *
     * If a constructor is annotated {@link JSONMetod} but does not follow
     * these rules, no decoder will be created for the class and an error
     * message will be logged.
     *
     * @param decodeClass  The Java class to construct a decoder for
     *
     * @throws JSONSetupError if an annotated constructor breaks the rules for
     *    a JSON-driven constructor.
     */
    private ObjectDecoder(Class decodeClass) {
        Constructor jsonConstructor = null;
        boolean includeRawObject = false;
        Class paramTypes[] = null;
        String paramNames[] = null;
        for (Constructor<?> constructor : decodeClass.getDeclaredConstructors()) {
            JSONMethod note = (JSONMethod) constructor.getAnnotation(JSONMethod.class);
            if (note == null) {
                continue;
            }

            if (jsonConstructor != null) {
                throw new JSONSetupError("class " + decodeClass.getName() +
                    " has more than one JSON constructor");
            }

            paramTypes = constructor.getParameterTypes();
            paramNames = note.value();
            if (paramNames.length + 1 == paramTypes.length) {
                if (!JSONObject.class.isAssignableFrom(paramTypes[0])) {
                    throw new JSONSetupError("class " + decodeClass.getName() +
                    " JSON constructor lacks a JSONObject first parameter");
                }
                includeRawObject = true;
            } else if (paramNames.length != paramTypes.length) {
                throw new JSONSetupError("class " + decodeClass.getName() +
                    " JSON constructor has wrong number of parameters");
            }
            jsonConstructor = constructor;
        }
        if (jsonConstructor == null) {
            throw new JSONSetupError("no JSON constructor for class " +
                                    decodeClass.getName());
        }
        myConstructor =
            new ConstructorInvoker(jsonConstructor, includeRawObject,
                                   paramTypes, paramNames);
    }

    /**
     * Obtain (by looking it up in theDecoders or by creating it) an
     * ObjectDecoder for a given class.
     *
     * @param decodeClass  The class whose decoder is sought
     *
     * @return a decoder for 'decodeClass', or null if one could not be made.
     */
    static private ObjectDecoder classDecoder(Class decodeClass) {
        ObjectDecoder decoder = theDecoders.get(decodeClass);

        if (decoder == null) {
            try {
                decoder = new ObjectDecoder(decodeClass);
                theDecoders.put(decodeClass, decoder);
            } catch (JSONSetupError e) {
                Trace.comm.errorm(e.getMessage());
                decoder = null;
            }
        }
        return decoder;
    }

    /**
     * Invoke the constructor specified by a JSON object descriptor and return
     * the resulting Java object.
     *
     * @param obj  The JSON object descriptor to decode.
     * @param resolver  An object mapping JSON type tag strings to classes.
     *
     * @return the Java object described by 'obj', or null if 'obj' could not
     *    be interpreted.
     */
    private Object decode(JSONObject obj, TypeResolver resolver) {
        return myConstructor.construct(obj, resolver);
    }

    /**
     * Produce the Java object described by a particular JSON object
     * descriptor.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     *    result will not necessarily be of this class, but will be assignable
     *    to a variable of this class.
     * @param obj  The parsed JSON object descriptor to be decoded.
     * @param resolver  An object mapping type tag strings to Java classes.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     *    described by 'obj', or null if the object could not be decoded for
     *    some reason.
     */
    static public Object decode(Class baseType, JSONObject obj,
                                TypeResolver resolver)
    {
        Object result = null;
        String typeName = obj.type();
        Class targetClass = null;
        if (typeName != null) {
            targetClass = resolver.resolveType(baseType, typeName);
            if (targetClass == null) {
                Trace.comm.errorm("no Java class associated with JSON type tag '" + typeName + "'");
            }
        } else {
            targetClass = baseType;
        }
        if (targetClass != null) {
            ObjectDecoder decoder = classDecoder(targetClass);
            if (decoder != null) {
                result = decoder.decode(obj, resolver);
            } else {
                Trace.comm.errorm("no decoder for " + targetClass);
            }
        }
        return result;
    }

    /**
     * A simple JSON object decoder for one-shot objects.  The given object is
     * by the {@link #decode(Class,JSONObject,TypeResolver)} method, using the
     * {@link StaticTypeResolver} to resolve type tags.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     *    result will not necessarily be of this class, but will be assignable
     *    to a variable of this class.
     * @param jsonObj  A JSON object describing the object to decode.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     *    described by 'jsonObj', or null if the object could not be decoded
     *    for some reason.
     */
    static public Object decode(Class baseType, JSONObject jsonObj) {
        return decode(baseType, jsonObj,
                      StaticTypeResolver.theStaticTypeResolver);
    }

    /**
     * A simple JSON string decoder for one-shot objects.  The given string is
     * first parsed, and then decoded as by the {@link
     * #decode(Class,JSONObject,TypeResolver)} method, using the {@link
     * StaticTypeResolver} to resolve type tags.
     *
     * @param baseType  The desired class of the resulting Java object.  The
     *    result will not necessarily be of this class, but will be assignable
     *    to a variable of this class.
     * @param str  A JSON string describing the object.
     *
     * @return a new Java object assignable to the class in 'baseType' as
     *    described by 'str', or null if the string was syntactically malformed
     *    or the object could not be decoded for some reason.
     */
    static public Object decode(Class baseType, String str) {
        try {
            Parser parser = new Parser(str);
            JSONObject jsonObj = parser.parseObjectLiteral();
            return decode(baseType, jsonObj);
        } catch (SyntaxError e) {
            Trace.comm.warningm("syntax error decoding object: " +
                                e.getMessage());
            return null;
        }
    }
}
