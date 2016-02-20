package org.elkoserver.json;

/**
 * Thrown when a there is a problem of some sort interpreting the contents of a
 * JSON object.
 */
public class JSONDecodingException extends Exception {
    public JSONDecodingException() {
    }
    public JSONDecodingException(String message) {
        super(message);
    }
    public JSONDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
    public JSONDecodingException(Throwable cause) {
        super(cause);
    }
}
