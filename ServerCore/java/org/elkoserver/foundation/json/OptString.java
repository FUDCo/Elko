package org.elkoserver.foundation.json;

/**
 * An optional JSON message parameter of type {@link String}.
 */
public class OptString extends OptionalParameter {
    /** Singleton instance of OptString with the value not present. */
    public static final OptString theMissingValue = new OptString();

    /** The actual String value */
    private String myValue;

    /**
     * Constructor (value present).
     *
     * @param value  The value of the parameter.
     */
    public OptString(String value) {
        super(true);
        myValue = value;
    }

    /**
     * Constructor (value absent).
     */
    private OptString() {
        super(false);
    }

    /**
     * Get the {@link String} value of the parameter.  It is an error if the
     * value is absent.
     *
     * @return the ({@link String}) value.
     *
     * @throws Error if the value is not present.
     */
    public String value() {
        if (isPresent) {
            return myValue;
        } else {
            throw new Error("extraction of value from non-present OptString");
        }
    }

    /**
     * Get the {@link String} value of this parameter, or a default value if
     * the value is absent.
     *
     * @param defaultValue  The default value for the parameter.
     *
     * @return the {@link String} value of this parameter if present, or the
     *    value of 'defaultValue' if not present.
     */
    public String value(String defaultValue) {
        if (isPresent) {
            return myValue;
        } else {
            return defaultValue;
        }
    }
}
