package org.elkoserver.foundation.json;

/**
 * Base class for various classes representing types for optional JSON message
 * parameters.
 */
abstract class OptionalParameter {
    /** Flag that value was present. */
    boolean isPresent;

    /**
     * Internal constructor.
     *
     * @param present  true=>value is present, false=>it's not
     */
    OptionalParameter(boolean present) {
        isPresent = present;
    }

    /**
     * Produce an OptionalParameter value representing an (allowed) missing
     * parameter of some type.
     *
     * @param type  The expected class of the missing parameter.
     *
     * @return the canonical missing value object for 'type'.
     */
    static Object missingValue(Class type) {
        if (!OptionalParameter.class.isAssignableFrom(type)) {
            return null;
        } else if (type == OptString.class) {
            return OptString.theMissingValue;
        } else if (type == OptBoolean.class) {
            return OptBoolean.theMissingValue;
        } else if (type == OptInteger.class) {
            return OptInteger.theMissingValue;
        } else if (type == OptDouble.class) {
            return OptDouble.theMissingValue;
        } else {
            return null;
        }
    }

    /**
     * Test if this parameter's value was present.
     *
     * @return true if the parameter value was present, false if not.
     */
    public boolean present() {
        return isPresent;
    }
}
