package org.elkoserver.json;

/**
 * Control object for regulating the behavior of an encoding operation.  When
 * an object is being serialized to JSON, the choice of the subset of the
 * object's state that actually gets written can vary depending on the intended
 * destination or use for the data.
 *
 * Currently, there are two cases: encoding for the client and encoding for the
 * repository.  However, additional cases are possible; for example, a
 * representation being sent to a client might vary depending on whether the
 * user of the client is the "owner" of the object in question or not.  This
 * class exists to provide a place to extend the range of options, though the
 * base case only supports the client vs. repository distinction.
 */
public class EncodeControl {
    /** Flag indicating that this encoding is for the client. */
    private final boolean amToClient;

    /** Flag indicating that this encoding is for the repository (for
        persisting the object). */
    private final boolean amToRepository;

    /** A global, encoding control representing the intention to encode for the
        client. */
    public static final EncodeControl forClient =
        new EncodeControl(true, false);

    /** A global, encoding control representing the intention to encode for the
        repository. */
    public static final EncodeControl forRepository =
        new EncodeControl(false, true);

    /**
     * Private constructor.
     */
    private EncodeControl(boolean toClient, boolean toRepository) {
        amToClient = toClient;
        amToRepository = toRepository;
    }

    /**
     * Test if this controller says to encode for the client.
     *
     * @return true if this should be a client encoding.
     */
    public boolean toClient() {
        return amToClient;
    }

    /**
     * Test if this controller says to encode for the repository.
     *
     * @return true if this should be a repository encoding.
     */
    public boolean toRepository() {
        return amToRepository;
    }
}
