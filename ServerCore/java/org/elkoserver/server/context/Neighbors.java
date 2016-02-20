package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.json.JSONLiteral;

/**
 * Deliverer object that delivers to all the members of a send group except
 * one.
 */
class Neighbors implements Deliverer {
    /** The send group whose member is being excluded. */
    private SendGroup myGroup;

    /** The member who is excluded. */
    private Deliverer myExclusion;

    /**
     * Constructor.
     *
     * @param group  The send group.
     * @param exclusion  Who to leave out.
     */
    Neighbors(SendGroup group, Deliverer exclusion) {
        myGroup = group;
        myExclusion = exclusion;
    }

    /**
     * Send a message to every member of the send group except the excluded
     * one.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        myGroup.sendToNeighbors(myExclusion, message);
    }
}
