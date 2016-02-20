package org.elkoserver.foundation.net;

/**
 * Class encapsulating an HTTP error that is being output as the reply to
 * an HTTP request.
 */
class HTTPError {
    /** HTTP error number, e.g. 404 for not found. */
    private int myErrorNumber;

    /** Error string that goes with the error number, e.g. "Not Found". */
    private String myErrorString;

    /** Message body HTML. */
    private String myMessageString;

    /**
     * Constructor.
     *
     * @param errorNumber  The HTTP error number (e.g., 404 for not found).
     * @param errorString  The error string (e.g., "Not Found").
     * @param messageString  The message body HTML.
     */
    HTTPError(int errorNumber, String errorString, String messageString) {
        myErrorNumber = errorNumber;
        myErrorString = errorString;
        myMessageString = messageString;
    }

    /**
     * Obtain the error number.
     *
     * @return the error number.
     */
    int errorNumber() {
        return myErrorNumber;
    }

    /**
     * Obtain the error string.
     *
     * @return the error string.
     */
    String errorString() {
        return myErrorString;
    }

    /**
     * Obtain the message string.
     *
     * @return the message string.
     */
    String messageString() {
        return myMessageString;
    }
}

