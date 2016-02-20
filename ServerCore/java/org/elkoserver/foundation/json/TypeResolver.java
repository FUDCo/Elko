package org.elkoserver.foundation.json;

/**
 * A mapping from JSON type tag strings to Java classes.  This interface only
 * specifies the lookup operation.  It is up to the implementor as to how the
 * mapping is established and maintained.
 */
public interface TypeResolver {
    /**
     * Determine the Java class associated with a given JSON type tag string.
     *
     * @param baseType  Base class from which result class must be derived.
     * @param typeName  JSON type tag identifying the desired class.
     *
     * @return a class named by 'typeName' suitable for assignment to a method
     *     or constructor parameter of class 'baseType'.
     */
    Class<?> resolveType(Class<?> baseType, String typeName);
}
