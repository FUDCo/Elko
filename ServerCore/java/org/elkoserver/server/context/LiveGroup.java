package org.elkoserver.server.context;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.json.JSONLiteral;

/**
 * The normal, ordinary implementation of {@link SendGroup}.
 */
class LiveGroup implements SendGroup {
    /** The objects in this send group. */
    private Set<Deliverer> myMembers;

    /**
     * Constructor.  Creates an empty group.
     */
    LiveGroup() {
        myMembers = new HashSet<Deliverer>();
    }

    /**
     * Add a new member to the send group.
     *
     * @param member  The new member to add.
     */
    public void admitMember(Deliverer member) {
        myMembers.add(member);
    }

    /**
     * Remove a member from the send group.
     *
     * @param member  The member to remove.
     */
    public void expelMember(Deliverer member) {
        myMembers.remove(member);
    }

    /**
     * Get a read-only view of the set of members of this send group.
     *
     * @return the current set of members of this group.
     */
    Set<Deliverer> members() {
        return Collections.unmodifiableSet(myMembers);
    }

    /**
     * Send a message to each member of this send group.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        for (Deliverer member : myMembers) {
            member.send(message);
        }
    }

    /**
     * Send a message to every member of this send group except one.
     *
     * @param exclude  The member to exclude from receiving the message.
     * @param message  The message to send.
     */
    public void sendToNeighbors(Deliverer exclude, JSONLiteral message) {
        for (Deliverer member : myMembers) {
            if (member != exclude) {
                member.send(message);
            }
        }
    }
}
