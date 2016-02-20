package org.elkoserver.foundation.json;

/**
 * An optional JSON message parameter of type int.
 */
public class OptInteger extends OptionalParameter {
    /** Singleton instance of OptInteger with the value not present. */
    public static final OptInteger theMissingValue = new OptInteger();

    /** The actual int value */
    private int myValue;

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    public OptInteger(int value) {
        super(true);
        myValue = value;
    }

    /**
     * Constructor (value absent).
     */
    private OptInteger() {
        super(false);
    }

    /**
     * Get the int value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (int) value.
     *
     * @throws Error if the value is not present.
     */
    public int value() {
        if (isPresent) {
            return myValue;
        } else {
            throw new Error("extraction of value from non-present OptInteger");
        }
    }

    /**
     * Get the int value of this parameter, or a default value if the value is
     * absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the int value of this parameter if present, or the value of
     *    'defaultValue' if not present.
     */
    public int value(int defaultValue) {
        if (isPresent) {
            return myValue;
        } else {
            return defaultValue;
        }
    }
}
