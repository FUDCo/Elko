package org.elkoserver.foundation.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple type resolver that tries to resolve JSON type tags from statically
 * available class information rather than from a lookup table.  It looks, in
 * the class (and its superclasses) for which it is trying to resolve a type
 * name, for a static method with the signature:
 *
 * <blockquote><tt> Class jsonClass(String typeName); </tt></blockquote>
 *
 * which it will then invoke to do the resolution.  If it fails to find such a
 * method, it will default to assuming that the goal class is itself the proper
 * result.
 */
public class StaticTypeResolver implements TypeResolver {
    /** The singleton instance of this resolver. */
    public static final StaticTypeResolver theStaticTypeResolver =
        new StaticTypeResolver();

    /** Cached mapping from Java class to the JSON object type tag lookup
        method for that class. */
    private Map<Class, Method> myMethodCache;

    /**
     * Private Miranda constructor to ensure singletonness.
     */
    private StaticTypeResolver() {
        myMethodCache = new HashMap<Class, Method>();
    }

    /**
     * Resolve a JSON type tag.  This is done by looking for a method named
     * 'jsonClass' in the class 'baseType' (or one of its superclasses), then
     * invoking this method to perform the resolution.  If such a method
     * doesn't exist, then assume that the class 'baseType' itself is the right
     * answer.
     *
     * @param baseType  The base class to which the type name should resolve
     *    (and from which a 'jsonClass' method will be sought).
     * @param typeName  The JSON type tag string whose resolution is sought.
     *
     * @return a suitable class for the name 'typeName' that can be assigned to
     *    a parameter of class 'baseType'.
     */
    public Class resolveType(Class baseType, String typeName) {
        Class<?> type = baseType;
        Method lookupMethod = myMethodCache.get(type);
        if (lookupMethod == null && myMethodCache.containsKey(type)) {
            return baseType;
        }
        while (lookupMethod == null && type != null) {
            try {
                lookupMethod =
                    type.getDeclaredMethod("jsonClass",
                                           new Class[] { String.class });
                if (Modifier.isStatic(lookupMethod.getModifiers())) {
                    lookupMethod.setAccessible(true);
                    myMethodCache.put(baseType, lookupMethod);
                } else {
                    lookupMethod = null;
                }
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            }
        }
        if (lookupMethod == null) {
            myMethodCache.put(baseType, null);
            return baseType;
        } else {
            try {
                return (Class) lookupMethod.invoke(null,
                                                   new Object[] { typeName });
            } catch (IllegalAccessException e) {
                return null;
            } catch (InvocationTargetException e) {
                return null;
            } catch (ClassCastException e) {
                return null;
            }
        }
    }
}
