package org.elkoserver.foundation.json;

/**
 * An error somewhere in the process of performing the reflection operations to
 * prepare to invoke methods or constructors from a JSON object.  This error
 * will happen only if the {@link JSONMethod} annotations on a JSON driven
 * method or constructor are incorrectly specified; this should never happen
 * during normal operation as a result of message receipt.
 */
public class JSONSetupError extends Error {
    /**
     * Construct a JSONSetupError with no specified detail message.
     */
    public JSONSetupError() {
        super();
    }

    /**
     * Construct a JSONSetupError with the specified detail message.
     *
     * @param message  The detail message.
     */
    public JSONSetupError(String message) {
        super(message);
    }
}
