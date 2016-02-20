package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.json.JSONLiteral;

/**
 * A limbo {@link SendGroup}, so actors can have a place to be when they aren't
 * anyplace.  Unlike a regular SendGroup, this one does not track its members:
 * anyone can declare themselves here or not, on their own say so.
 */
class LimboGroup implements SendGroup {
    /** Singleton LimboGroup instance.  Since all limbo groups are the same,
        everyone might as well use this one. */
    final static LimboGroup theLimboGroup = new LimboGroup();

    /**
     * Private constructor, so nobody can make more.
     *
     * Not only might everyone just as well use the singleton above, I guess
     * they sorta have to, eh?
     */
    private LimboGroup() {
        /* Nothing here. */
    }

    /**
     * Add a new member to the group.  This is a no-op, since the limbo group
     * doesn't track its members.
     *
     * @param member  The new member to add.
     */
    public void admitMember(Deliverer member) {
        /* Nothing here. */
    }

    /**
     * Remove a member from the group.  This is a no-op, since the limbo group
     * doesn't track its members.
     *
     * @param member  The member to remove.
     */
    public void expelMember(Deliverer member) {
        /* Nothing here. */
    }

    /**
     * Send a message to every member of this group.  Messages sent to the
     * limbo group are simply discarded.
     *
     * @param message  The message to (not) send.
     */
    public void send(JSONLiteral message) {
        /* Nothing here. */
    }

    /**
     * Send a message to every member of this group except one.  Messages
     * sent to the limbo group are simply discarded.
     *
     * @param exclude  The member to exclude from receiving the message.
     * @param message  The message to send.
     */
    public void sendToNeighbors(Deliverer exclude, JSONLiteral message) {
        /* Nothing here. */
    }
}
