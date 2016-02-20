package org.elkoserver.server.director;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.elkoserver.util.HashSetMulti;

/**
 * Information describing an open context.
 */
class OpenContext {
    /** The provider for this context. */
    private Provider myProvider;

    /** Context name. */
    private String myName;

    /** Context clone set name, if a clone (null if not). */
    private String myCloneSetName;

    /** Was this context was created at the behest of this director? */
    private boolean amMine;

    /** True if this context is a clone. */
    private boolean amClone;

    /** True if this context is restricted from entry by strangers. */
    private boolean amRestricted;

    /** Maximum number of users allowed, or -1 if unlimited. */
    private int myMaxCapacity;

    /** Number of users allowed before preferring a different clone. */
    private int myBaseCapacity;

    /** Users in this context. */
    private Set<String> myUsers;

    /** User clones in this context. */
    private HashSetMulti<String> myUserClones;

    /** Reason this context is closed to user entry, or null if it is not. */
    private String myGateClosedReason;

    /**
     * Constructor.
     *
     * @param provider  Who is providing the context.
     * @param name  Context name.
     * @param mine  true if the context was opened by request of this director.
     * @param maxCapacity  The maximum user capacity for the context.
     * @param baseCapacity  The base capacity for the (clone) context.
     * @param restricted  true if the context is entry restricted
     */
    OpenContext(Provider provider, String name, boolean mine, int maxCapacity,
                int baseCapacity, boolean restricted) {
        myProvider = provider;
        myName = name;
        amMine = mine;
        amRestricted = restricted;
        myMaxCapacity = maxCapacity;
        myBaseCapacity = baseCapacity;
        myUsers = new HashSet<String>();
        myUserClones = new HashSetMulti<String>();
        myGateClosedReason = null;

        int dashPos = 0;
        int dashCount = 0;
        while (dashPos >= 0) {
            dashPos = name.indexOf('-', dashPos);
            if (dashPos >= 0) {
                ++dashCount;
                ++dashPos;
            }
        }
        if (dashCount > 1) {
            amClone = true;
            dashPos = name.lastIndexOf('-');
            myCloneSetName = name.substring(0, dashPos);
        } else {
            amClone = false;
            myCloneSetName = null;
        }
    }

    /**
     * Add a user to this context.
     *
     * @param user  The name of the user to add.
     */
    void addUser(String user) {
        myUsers.add(user);
        if (Director.isUserClone(user)) {
            myUserClones.add(Director.userCloneSetName(user));
        }
    }

    /**
     * Get this context's clone set name.
     *
     * @return the name of this context's clone set.
     */
    String cloneSetName() {
        return myCloneSetName;
    }

    /**
     * Close this context's gate, blocking new users from entering.
     *
     * @param reason  String describing why this is being done.
     */
    void closeGate(String reason) {
        if (reason == null) {
            myGateClosedReason = "context closed to new entries";
        } else {
            myGateClosedReason = reason;
        }
    }

    /**
     * Obtain a string describing the reason this context's gate is closed.
     *
     * @return a reason string for this context's gate closure, or null if the
     *   gate is open.
     */
    String gateClosedReason() {
        return myGateClosedReason;
    }

    /**
     * Test if this context's gate is closed.  If the gate is closed, new users
     * may not enter, even if the context is not full.
     *
     * @return true iff this context's gate is closed.
     */
    boolean gateIsClosed() {
        return myGateClosedReason != null;
    }

    /**
     * Test if this context is a clone.
     *
     * @return true if this context is a clone.
     */
    boolean isClone() {
        return amClone;
    }

    /**
     * Test if this context has reached its maximum capacity.
     *
     * @return true if this context cannot accept any more members.
     */
    boolean isFull() {
        if (myMaxCapacity < 0) {
            return false;
        } else {
            return myUsers.size() >= myMaxCapacity;
        }
    }

    /**
     * Test if this context has reached or exceeded its base capacity.
     *
     * @return true if this context is at or above its base capacity.
     */
    boolean isFullClone() {
        if (myBaseCapacity < 0) {
            return false;
        } else {
            return myUsers.size() >= myBaseCapacity;
        }
    }

    /**
     * Test if this context is was started up by order of this director.
     *
     * @return true if this context was created by this director.
     */
    boolean isMine() {
        return amMine;
    }

    /**
     * Test if entry to this context is restricted from entry by random users
     * requesting reservations.
     *
     * @return true if this is a restricted context.
     */
    boolean isRestricted() {
        return amRestricted;
    }

    /**
     * Test if a given user is in this context.
     *
     * @param user  The name of the user to test for.
     */
    boolean hasUser(String user) {
        return myUsers.contains(user) || myUserClones.contains(user);
    }

    /**
     * Get the name of this context.
     *
     * @return this context's name.
     */
    String name() {
        return myName;
    }

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    void openGate() {
        myGateClosedReason = null;
    }

    /**
     * Given a context that is a duplicate of this one, pick the one that
     * should be closed to eliminate the duplication.  The victim is the one
     * whose provider has the lexically lowest dupKey() value.
     *
     * @param other  The other context to compare against.
     *
     * @return the context (this or other) that should be closed.
     */
    OpenContext pickDupToClose(OpenContext other) {
        String thisKey = myProvider.dupKey();
        String otherKey = other.myProvider.dupKey();
        if (thisKey.compareTo(otherKey) < 0) {
            return this;
        } else {
            return other;
        }
    }

    /**
     * Get the provider for this context.
     *
     * @return the provider that is running this context.
     */
    Provider provider() {
        return myProvider;
    }

    /**
     * Remove a user from this context.
     *
     * @param user  The name of the user to remove.
     */
    void removeUser(String user) {
        myUsers.remove(user);
        if (Director.isUserClone(user)) {
            myUserClones.remove(Director.userCloneSetName(user));
        }
    }

    /**
     * Get the number of users currently in this context.
     *
     * @return the number of users in the context described by this object.
     */
    int userCount() {
        return myUsers.size();
    }

    /**
     * Get a read-only view of the names of the users in this context.
     *
     * @return a set of this context's user names.
     */
    Set<String> users() {
        return Collections.unmodifiableSet(myUsers);
    }
}
