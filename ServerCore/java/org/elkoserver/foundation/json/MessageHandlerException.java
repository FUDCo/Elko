package org.elkoserver.foundation.json;

/**
 * An exception in the execution of a JSON method.
 */
public class MessageHandlerException extends Exception {
    /**
     * Construct a MessageHandlerException with no specified detail message.
     */
    public MessageHandlerException() {
        super();
    }

    /**
     * Construct a MessageHandlerException with the specified detail message.
     *
     * @param message  The detail message.
     */
    public MessageHandlerException(String message) {
        super(message);
    }

    /**
     * Construct a MessageHandlerException wrapping some other kind of
     * exception.
     *
     * @param message  The detail message.
     * @param nested  Other exception to wrap.
     */
    public MessageHandlerException(String message, Throwable nested) {
        super(message, nested);
    }
}
