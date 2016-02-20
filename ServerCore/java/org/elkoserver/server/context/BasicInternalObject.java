package org.elkoserver.server.context;

/**
 * Utitlity base class for internal objects, providing a general implementation
 * of the InternalObject interface that should be suitable for most uses.
 */
abstract public class BasicInternalObject implements InternalObject {
    /** The contextor for this server. */
    private Contextor myContextor;

    /** The name by which this object will be addressed in messages. */
    private String myRef;

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     *    object table.
     * @param contextor  The contextor for this server.
     */
    public void activate(String ref, Contextor contextor) {
        myRef = ref;
        myContextor = contextor;
    }

    /**
     * Obtain the contextor for this server.
     *
     * @return the Contextor object for this server.
     */
    public Contextor contextor() {
        return myContextor;
    }

    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     *    messages, either as the message target or as a parameter value.
     */
    public String ref() {
        return myRef;
    }
}
