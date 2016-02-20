package org.elkoserver.foundation.json;

/**
 * An optional JSON message parameter of type double.
 */
public class OptDouble extends OptionalParameter {
    /** Singleton instance of OptDouble with the value not present. */
    public static final OptDouble theMissingValue = new OptDouble();

    /** The actual double value */
    private double myValue;

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    public OptDouble(double value) {
        super(true);
        myValue = value;
    }

    /**
     * Constructor (value absent).
     */
    private OptDouble() {
        super(false);
    }

    /**
     * Get the double value of the parameter.  It is an error if the value is
     * absent.
     *
     * @return the (double) value.
     *
     * @throws Error if the value is not present.
     */
    public double value() {
        if (isPresent) {
            return myValue;
        } else {
            throw new Error("extraction of value from non-present OptDouble");
        }
    }

    /**
     * Get the double value of this parameter, or a default value if the value
     * is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the double value of this parameter if present, or the value of
     * 'defaultValue' if not present.
     */
    public double value(double defaultValue) {
        if (isPresent) {
            return myValue;
        } else {
            return defaultValue;
        }
    }
}
