package org.elkoserver.foundation.json;

/**
 * An exception when the parameters of an invoked JSON method or JSON-driven
 * constructor don't match what was expected.
 */
class ParameterMismatchException extends RuntimeException {
    /**
     * Constructor with no specified detail message.
     */
    ParameterMismatchException() {
        super();
    }

    /**
     * Constructor.  Generates a detail message given descriptions of what
     * parameters were supplied vs. what were expected.
     *
     * @param suppliedParams  The supplied parameter objects.
     * @param expectedParams  The classes of the expected parameters.
     */
    ParameterMismatchException(Object[] suppliedParams, Class[] expectedParams)
    {
        super(createMessageString(suppliedParams, expectedParams));
    }

    /**
     * Generates a detail message for an exception, the parameters that were
     * supplied and the classes that what were expected.
     *
     * @param suppliedParams  The supplied parameter objects.
     * @param expectedParams  The classes of the expected parameters.
     */
    private static String createMessageString(Object[] suppliedParams,
                                              Class<?>[] expectedParams)
    {
        String message = "";
        int count = Math.min(suppliedParams.length, expectedParams.length);
        for (int i = 0; i < count; ++i) {
            if (!expectedParams[i].isAssignableFrom(
                    suppliedParams[i].getClass())) {
                message = message + "Parameter mismatch: Method  requires "
                    + expectedParams[i] + "; found "
                    + suppliedParams[i].getClass() + ". ";
            }
        }
        return message;
    }
}
