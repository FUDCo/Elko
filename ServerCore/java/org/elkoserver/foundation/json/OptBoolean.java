package org.elkoserver.foundation.json;

/**
 * An optional JSON message parameter of type boolean.
 */
public class OptBoolean extends OptionalParameter {
    /** Singleton instance of OptBoolean with the value not present. */
    public static final OptBoolean theMissingValue = new OptBoolean();

    /** The actual boolean value */
    private boolean myValue;

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter
     */
    public OptBoolean(boolean value) {
        super(true);
        myValue = value;
    }

    /**
     * Constructor (value absent).
     */
    private OptBoolean() {
        super(false);
    }

    /**
     * Get the boolean value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (boolean) value.
     *
     * @throws Error if the value is not present.
     */
    public boolean value() {
        if (isPresent) {
            return myValue;
        } else {
            throw new Error("extraction of value from non-present OptBoolean");
        }
    }

    /**
     * Get the boolean value of this parameter, or a default value if the value
     * is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the boolean value of this parameter if present, or the value of
     *    'defaultValue' if not present.
     */
    public boolean value(boolean defaultValue) {
        if (isPresent) {
            return myValue;
        } else {
            return defaultValue;
        }
    }
}
