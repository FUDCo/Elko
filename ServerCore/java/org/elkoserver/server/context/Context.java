package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.trace.Trace;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A {@link Context} is a place for interaction between connected users.  It
 * is one of the three basic object types (along with {@link User} and {@link
 * Item}).
 */
public class Context extends BasicObject implements Deliverer {
    /** Send group for users in this context. */
    private LiveGroup myGroup;

    /** Maximum number of users allowed in the context at any one time (-1 for
        unlimited). */
    private int myMaxCapacity;

    /** Maximum number of users allowed in the context before it clones. */
    private int myBaseCapacity;

    /** True if users in this context can't see one another. */
    private boolean amSemiPrivate;

    /** True if entry to this context is access controlled. */
    private boolean amEntryRestricted;

    /** True if anonymous users are allowed in this context. */
    private boolean amAllowAnonymous;

    /** True if this context's contents aren't managed by the context itself */
    private boolean amContentAgnostic;

    /** True if this context may be used as a template for other contexts */
    private boolean amAllowableTemplate;

    /** True if this context may only be used as a template */
    private boolean amMandatoryTemplate;

    /** Mods to attach to users when they arrive. */
    private Mod myUserMods[];

    /** Presence domains that this context subscribes to.  Empty if not
        subscribing to any, null if not providing presence information. */
    private String mySubscriptions[];


    /* Fields below here only apply to active contexts. */

    /** Number of users currently in the context. */
    private int myUserCount;

    /** Users here by base ref, or null if this context is multientry. */
    private Map<String, User> myUsers;

    /** Number of retainers holding the context open. */
    private int myRetainCount;

    /** Ref of context descriptor from which this context was loaded. */
    private String myLoadedFromRef;

    /** Director who originally requested this context to be opened, if any. */
    private DirectorActor myOpener;

    /** Entities that want to be notified when users arrive or depart. */
    private List<UserWatcher> myUserWatchers;

    /** Entities that want to be notified when the context is shut down. */
    private List<ContextShutdownWatcher> myContextShutdownWatchers;

    /** True if context is being shut down, thus blocking new entries. */
    private boolean amClosing;

    /** True if context shut down is being forced, ignoring retain count */
    private boolean amForceClosing;

    /** Reason this context is closed to user entry, or null if it is not. */
    private String myGateClosedReason;

    /** Optional watcher for friend presence changes. */
    private PresenceWatcher myPresenceWatcher;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * JSON-driven constructor.
     *
     * @param name  Context name.
     * @param maxCapacity   Maximum number of users allowed (-1 if unlimited).
     * @param baseCapacity   Maximum number of users before cloning (-1, the
     *    default, if context is not to be cloned).
     * @param isSemiPrivate  Flag that context is semi-private (false by
     *    default).
     * @param isEntryRestricted  Flag that context is subject to entry
     *    restriction (false by default).
     * @param isContentAgnostic  Flag that context has no beliefs about its
     *    contents (false by default).
     * @param isMultiEntry  Flag that is true if a user may enter this context
     *    multiple times concurrently (false by default).
     * @param mods Mods for the new context (null if it has no mods).
     * @param userMods  Templates for mods to attach to entering users.
     * @param ref  Optional reference string for this context object.
     * @param subscribe  Optional array of presence domains to subscribe to
     * @param isEphemeral  Flag that context is ephemeral, i.e., changes won't
     *    be persisted) (false by default)
     * @param isAllowableTemplate  Flag that context may be used as a template
     *    for other contexts (false by default).
     * @param isMandatoryTemplate  Flag that context may be only used as a
     *    template for other contexts, but not instantiated directly (false by
     *    default).
     * @param isAllowAnonymous  Flag that context permits anonymous users to
     *    enter (false by default).
     */
    @JSONMethod({"name", "capacity", "basecapacity", "semiprivate",
                 "restricted", "agnostic", "multientry", "mods", "usermods",
                 "contents", "ref", "?subscribe", "ephemeral", "template",
                 "templateonly", "allowanonymous" })
    Context(String name, int maxCapacity, OptInteger baseCapacity,
                OptBoolean isSemiPrivate, OptBoolean isEntryRestricted,
                OptBoolean isContentAgnostic, OptBoolean isMultiEntry,
                Mod mods[], Mod userMods[], Item contents[], OptString ref,
                String subscribe[], OptBoolean isEphemeral,
                OptBoolean isAllowableTemplate, OptBoolean isMandatoryTemplate,
                OptBoolean isAllowAnonymous)
    {
        super(name, mods, true, contents, null);
        myLoadedFromRef = null;
        myMaxCapacity = maxCapacity;
        myBaseCapacity = baseCapacity.value(-1);
        amSemiPrivate = isSemiPrivate.value(false);
        amEntryRestricted = isEntryRestricted.value(false);
        amAllowAnonymous = isAllowAnonymous.value(false);
        amContentAgnostic = isContentAgnostic.value(false);
        amAllowableTemplate = isAllowableTemplate.value(false);
        amMandatoryTemplate = isMandatoryTemplate.value(false);
        if (isEphemeral.value(false)) {
            markAsEphemeral();
        }
        if (!isMultiEntry.value(false)) {
            myUsers = new HashMap<String, User>();
        }
        myUserMods = userMods;
        myPresenceWatcher = null;
        mySubscriptions = subscribe;
        myGateClosedReason = null;
    }

    /**
     * Activate a context.
     *
     * @param ref  Reference string for the new context.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param isEphemeral  True if this context is ephemeral (won't checkpoint)
     * @param contextor  Contextor for this server.
     * @param loadedFromRef  Reference string for the context descriptor that
     *    this context was loaded from
     * @param opener Director who requested this context to be opened, or null
     *    if not relevant.
     * @param appTrace  Trace object for diagnostics.
     */
    void activate(String ref, String subID, boolean isEphemeral,
                  Contextor contextor, String loadedFromRef,
                  DirectorActor opener, Trace appTrace)
    {
        super.activate(ref, subID, isEphemeral, contextor);
        tr = appTrace;
        myGroup = new LiveGroup();
        myUserCount = 0;
        myRetainCount = 0;
        myUserWatchers = null;
        myContextShutdownWatchers = null;
        myOpener = opener;
        myLoadedFromRef = loadedFromRef;
        amClosing = false;
        amForceClosing = false;
        contextor.noteContext(this, true);
    }

    /**
     * Add a new mod to this context.  The mod must be a {@link ContextMod}
     * even though the method is declared generically.  If it is not, it will
     * not be added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be a {@link ContextMod}.
     */
    void attachMod(Mod mod) {
        if (mod instanceof ContextMod) {
            super.attachMod(mod);
            if (mod instanceof PresenceWatcher) {
                myPresenceWatcher = (PresenceWatcher) mod;
            }
        } else {
            tr.errorm("attempt to attach non-ContextMod " + mod + " to " +
                      this);
        }
    }

    /**
     * Attach the context-specified user mods for this context to a user.
     *
     * @param who  The user to attach the mods to.
     */
    void attachUserMods(User who) {
        if (myUserMods != null) {
            for (Mod mod : myUserMods) {
                try {
                    Mod newMod = (Mod) mod.clone();
                    newMod.markAsEphemeral();
                    newMod.attachTo(who);
                } catch (CloneNotSupportedException e) {
                    tr.errorm("Mod class " + mod.getClass() +
                              " does not support clone");
                }
            }
        }
    }

    /**
     * Obtain the number of users who may enter (a clone of) this context
     * before another clone must be created.  When the user count of the
     * context reaches this number, users may still enter this specific clone
     * if they specify its cloned context ID explicitly (and as long as {@link
     * #maxCapacity()} is not exceeded), but users who request entry to the
     * context by specifying its generic context ID (that is, its ID before
     * cloning) will be directed to a different clone.
     *
     * @return the number of users who may enter before the context clones.
     */
    public int baseCapacity() {
        return myBaseCapacity;
    }

    /**
     * If nobody is using this context any more, checkpoint and discard it.
     */
    private void checkForContextShutdown() {
        if (myUserCount == 0 && (myRetainCount == 0 || amForceClosing)) {
            if (!amClosing) {
                amClosing = true;
                tr.eventi("shutting down " + this);
                noteContextShutdown();
                checkpoint();
                myContextor.remove(this);
                myContextor.noteContext(this, false);
            }
        }
    }

    /**
     * Close this context's gate, blocking new users from entering.
     *
     * @param reason  String describing why this is being done.
     */
    public void closeGate(String reason) {
        if (reason == null) {
            myGateClosedReason = "context closed to new entries";
        } else {
            myGateClosedReason = reason;
        }
        myContextor.noteContextGate(this, false, reason);
    }

    /**
     * Place a user into the context.
     *
     * @param who  The user to place.
     *
     * @return null if successful, or an error message string if not.
     */
    String enterContext(User who) {
        /* This looks like a bug, but isn't. It is correct to increment the
           count here, even if entry ends up being prevented, since a blocked
           entry will result in user exit and thus a call to exitContext()
           that will decrement the count again. */
        myUserCount += 1;

        if (amEntryRestricted && !who.entryEnabled(myRef)) {
            tr.eventi(who + " forbidden entry to " + this +
                      " (entry restricted)");
            return "restricted";
        } else if (!amAllowAnonymous && who.isAnonymous()) {
            tr.eventi(who + " forbidden entry to " + this +
                      " (anonymous users forbidden)");
            return "noanonymity";
        } else if (myGateClosedReason != null) {
            tr.eventi(who + " forbidden entry to " + this +
                      " (gate closed: " + myGateClosedReason + ")");
            return "gateclosed";
        } else if (myUserCount > myMaxCapacity && myMaxCapacity != -1) {
            tr.eventi(who + " forbidden entry to " + this +
                      " (capacity limit reached)");
            return "full";
        } else if (amClosing) {
            tr.eventi(who + " forbidden entry to " + this +
                      " (context is closing)");
            return "contextclose";
        } else {
            if (myUsers != null) {
                User prev = myUsers.get(who.baseRef());
                if (prev != null && !who.isEphemeral()) {
                    tr.eventi("expelling " + prev + " from " + this +
                              " due to reentry as " + who);
                    prev.send(Msg.msgExit(this, "duplicate entry", "dupentry",
                                          false));
                    prev.forceDisconnect();
                }
                myUsers.put(who.baseRef(), who);
            }
            sendContextDescription(who, myContextor.session());
            noteUserArrival(who);
            tr.eventi(who + " enters " + this);
            return null;
        }
    }

    /**
     * Remove a user from the context.
     *
     * @param who  The user to remove.
     */
    void exitContext(User who) {
        tr.eventi(who + " exits " + this);
        if (myUsers != null) {
            myUsers.remove(who.baseRef());
        }
        if (who.isArrived()) {
            if (!amSemiPrivate) {
                send(Msg.msgDelete(who));
            }
            noteUserDeparture(who);
        }
        --myUserCount;
        checkForContextShutdown();
    }

    /**
     * Close this context, even if it has been retained by one or more calls to
     * the {@link #retain} method, and even if there are still users in it
     * (this means kicking those users off).
     */
    public void forceClose() {
        forceClose(false);
    }

    /**
     * Close this context, even if it has been retained by one or more calls to
     * the {@link #retain} method, and even if there are still users in it
     * (this means kicking those users off).
     *
     * @param dup  true if this is being done to eliminate a duplicate context.
     */
    void forceClose(boolean dup) {
        amForceClosing = true;
        List<Deliverer> members = new LinkedList<Deliverer>(myGroup.members());
        for (Deliverer member : members) {
            User user = (User) member;
            user.exitContext("context closing", "contextclose", dup);
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
    public boolean gateIsClosed() {
        return myGateClosedReason != null;
    }

    /**
     * Look up an object in this context's namespace.  Note that the context's
     * namespace is not the context's contents but the namespace of object
     * identifiers that the context uses for resolving object references.  This
     * may be (indeed, normally is) shared with other active contexts on the
     * same server.
     *
     * @param ref  Reference string denoting the object desired.
     *
     * @return the object corresponding to 'ref', or null if there is no such
     *    object in the context's namespace.
     */
    public BasicObject get(String ref) {
        if (ref.equals("context")) {
            return this;
        } else {
            return (BasicObject) myContextor.get(ref);
        }
    }

    /**
     * Look up one of this server's static objects.
     *
     * @param ref  Reference string denoting the object of interest.
     *
     * @return the static object corresponding to 'ref', or null if there is no
     *    such object in the server's static object table.
     */
    public Object getStaticObject(String ref) {
        return myContextor.getStaticObject(ref);
    }

    /**
     * Obtain this context's send group.
     *
     * @return the send group for this context.
     */
    SendGroup group() {
        return myGroup;
    }

    /**
     * Test if this context may be used as a template for other contexts.
     *
     * @return true iff this context is an allowable template context.
     */
    boolean isAllowableTemplate() {
        return amAllowableTemplate || amMandatoryTemplate;
    }

    /**
     * Test if this context may be used as a template for other contexts but
     * may not be instantiated directly.
     *
     * @return true iff this context is a mandatory template context.
     */
    boolean isMandatoryTemplate() {
        return amMandatoryTemplate;
    }

    /**
     * Test if this context is semi-private.  In a semi-private context, users
     * appear to be in the context by themselves.  They don't see each other
     * come or go, and the 'push' and 'say' messages do not fan to the entire
     * context.  If mods wish to comply with the semi-private context concept
     * (most won't), they need to use this test.
     *
     * @return the state of this context's semi-private flag.
     */
    public boolean isSemiPrivate() {
        return amSemiPrivate;
    }

    /**
     * Test if this context is restricted, that is, whether it is closed to
     * entry without an internally requested reservation.
     *
     * @return true iff this is a restricted context.
     */
    public boolean isRestricted() {
        return amEntryRestricted;
    }

    /**
     * Obtain the ref of the context descriptor from which this context was
     * loaded.
     */
    public String loadedFromRef() {
        return myLoadedFromRef;
    }

    /**
     * Obtain the number of users who may enter before no more are allowed in.
     *
     * @return the number of users who may enter before the context becomes
     *    full.
     */
    public int maxCapacity() {
        return myMaxCapacity;
    }

    /**
     * Notify anybody who has expressed an interest in this context shutting
     * down.
     */
    private void noteContextShutdown() {
        if (myContextShutdownWatchers != null) {
            for (ContextShutdownWatcher watcher : myContextShutdownWatchers) {
                watcher.noteContextShutdown();
            }
        }
    }

    /**
     * Notify anybody who has expressed an interest that somebody has arrived
     * in this context.
     *
     * @param who  The user who arrived
     */
    private void noteUserArrival(User who) {
        if (myUserWatchers != null) {
            for (UserWatcher watcher : myUserWatchers) {
                watcher.noteUserArrival(who);
            }
        }
    }

    /**
     * Notify anybody who has expressed an interest that somebody has departed
     * from this context.
     *
     * @param who  The user who departed
     */
    private void noteUserDeparture(User who) {
        if (myUserWatchers != null) {
            for (UserWatcher watcher : myUserWatchers) {
                watcher.noteUserDeparture(who);
            }
        }
    }

    /**
     * Take notice that a user elsewhere has come or gone.
     *
     * @param observerRef  Ref of user in this context who allegedly cares
     * @param domain  Presence domain of relationship between users
     * @param whoRef  Ref of user who came or went
     * @param whereRef  Ref of context the entered or exited
     * @param on  True if they came, false if they left
     */
    void observePresenceChange(String observerRef, String domain,
                               String whoRef, String whereRef, boolean on)
    {
        if (myPresenceWatcher != null) {
            myPresenceWatcher.notePresenceChange(observerRef, domain, whoRef,
                                                 whereRef, on);
        }
        User observer = myUsers.get(observerRef);
        if (observer != null) {
            observer.observePresenceChange(observerRef, domain, whoRef,
                                           whereRef, on);
        } else {
            tr.warningi("presence change of " + whoRef +
                        (on ? " entering " : " exiting ") + whereRef +
                        " for context " + ref() +
                        " directed to unknown user " + observerRef);
        }
    }

    /**
     * Obtain the director who opened this context.
     *
     * @return the director who asked for this context to be opened.
     */
    DirectorActor opener() {
        return myOpener;
    }

    /**
     * Open this context's gate, allowing new users in if the context is not
     * full.
     */
    public void openGate() {
        myGateClosedReason = null;
        myContextor.noteContextGate(this, true, null);
    }

    /**
     * Register a callback to be invoked when the context is shut down.  Any
     * number of such callbacks may be registered.  The callback will be
     * invoked after all users have gone but immediately before the context is
     * checkpointed.  In particular, shutdown watchers may make changes to the
     * persistable state that will be checkpointed when the context is finally
     * shut down.
     *
     * @param watcher  An object to notify when the context is shut down.
     */
    public void registerContextShutdownWatcher(ContextShutdownWatcher watcher)
    {
        if (myContextShutdownWatchers == null) {
            myContextShutdownWatchers =
                new LinkedList<ContextShutdownWatcher>();
        }
        myContextShutdownWatchers.add(watcher);
    }

    /**
     * Register a callback to be invoked when a user enters or exits the
     * context.  Any number of such callbacks may be registered.
     *
     * @param watcher  An object to notify when a user arrives.
     */
    public void registerUserWatcher(UserWatcher watcher) {
        if (myUserWatchers == null) {
            myUserWatchers = new LinkedList<UserWatcher>();
        }
        myUserWatchers.add(watcher);
    }

    /**
     * Release an earlier call to {@link #retain}.  When {@link #release} has
     * been called the same number of times as {@link #retain} has been, the
     * context is free to shut down when empty.  If the context is already
     * empty, it will be shut down immediately.  Calls to this method in excess
     * of the number of calls to {@link #retain} will be ignored.
     */
    public void release() {
        if (myRetainCount > 0) {
            --myRetainCount;
            checkForContextShutdown();
        }
    }

    /**
     * Keep this context open even if all users exit (normally a context will
     * be shut down automatically after the last user leaves).  Each call to
     * {@link #retain} must be matched by a corresponding call to {@link
     * #release} in order for the context to be permitted to close normally
     * (though it can still be closed by called {@link #forceClose}).
     */
    public void retain() {
        myRetainCount += 1;
    }

    /**
     * Schedule a timer event associated with this context.  This is different
     * from scheduling a timer event directly using the Timer class in two
     * significant ways: first, it ensures that the context is retained until
     * after the event happens; second, it executes the event handler thunk on
     * the server's run queue instead of in the Timer thread, so that we won't
     * get reentrancy.
     *
     * Another notable difference is that unlike direct Timer events, there is
     * no explicit cancellation mechanism.  However, since the Timer's
     * cancellation mechanism is not really as useful as it might at first
     * appear, this is not as signficant.
     *
     * @param millis  How long to wait until timing out.
     * @param thunk  Thunk to be run when the timeout happens.
     */
    public void scheduleContextEvent(long millis, Runnable thunk) {
        retain();
        Timer.theTimer().after(millis, new ContextEventThunk(thunk));
    }

    /**
     * Class to hold onto a context event thunk so that when the timer triggers
     * the event, the thunk is executed on the server run queue and the context
     * is then released.
     */
    private class ContextEventThunk implements Runnable, TimeoutNoticer {
        private Runnable myThunk;

        ContextEventThunk(Runnable thunk) {
            myThunk = thunk;
        }

        public void noticeTimeout() {
            myContextor.server().enqueue(this);
        }

        public void run() {
            try {
                myThunk.run();
            } finally {
                release();
            }
        }
    }

    /**
     * Transmit a description of this context as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    public void sendObjectDescription(Deliverer to, Referenceable maker) {
        sendContextDescription(to, maker);
    }

    /**
     * Transmit a description of this context as a series of "make" messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     */
    void sendContextDescription(Deliverer to, Referenceable maker) {
        String sess = null;
        if (to instanceof User) {
            sess = ((User) to).sess();
        }
        to.send(Msg.msgMake(maker, this, sess));
        if (!amSemiPrivate) {
            for (Deliverer member : myGroup.members()) {
                if (member instanceof User) {
                    ((User) member).sendUserDescription(to, this, false);
                }
            }
        }
        if (!amContentAgnostic) {
            Contents.sendContentsDescription(to, this, myContents);
        }
        to.send(Msg.msgReady(this));
    }

    /**
     * Send a message to everyone in this context save one.  This message will
     * be delivered to every client whose user is currently in the context
     * except the one specified by the 'exclude' parameter.
     *
     * @param exclude  Who to exclude from the send operation.
     * @param message  The message to send.
     */
    public void sendToNeighbors(Deliverer exclude, JSONLiteral message) {
        myGroup.sendToNeighbors(exclude, message);
    }

    String[] subscriptions() {
        return mySubscriptions;
    }

    /**
     * Obtain a Deliverer that will deliver to all of a user's neighbors in
     * this context.
     *
     * @param exclude  Who to exclude from the send operation.
     *
     * @return a Deliverer that wraps Context.sendToNeighbors
     */
    public Deliverer neighbors(final Deliverer exclude) {
        return new Deliverer() {
            public void send(JSONLiteral message) {
                sendToNeighbors(exclude, message);
            }
        };
    }

    /**
     * Obtain a Deliverer that will deliver to an arbitrary list of users.
     *
     * @param toList  List of users to deliver to
     *
     * @return a Deliverer that wraps toList
     */
    public static Deliverer toList(final List<BasicObject> toList) {
        return new Deliverer() {
            public void send(JSONLiteral message) {
                for (BasicObject to : toList) {
                    User toUser = (User) to;
                    toUser.send(message);
                }
            }
        };
    }

    /**
     * Obtain a Deliverer that will deliver to an arbitrary list of users
     * except for one distinguished user.
     *
     * @param toList  List of users to deliver to
     * @param exclude  The one to exclude
     *
     * @return a Deliverer that wraps toList, taking note to exclude the one
     *    odd user out
     */
    public static Deliverer toListExcluding(final List<BasicObject> toList,
                                            final Deliverer exclude)
    {
        return new Deliverer() {
            public void send(JSONLiteral message) {
                for (BasicObject to : toList) {
                    User toUser = (User) to;
                    if (toUser != exclude) {
                        toUser.send(message);
                    }
                }
            }
        };
    }

    /**
     * Obtain a printable string representation of this context.
     *
     * @return a printable representation of this context.
     */
    public String toString() {
        return "Context '" + ref() + "'";
    }

    /**
     * Obtain a trace object for logging.
     *
     * @return a trace object for generating log messages from this context.
     */
    public Trace trace() {
        return tr;
    }

    /**
     * Get the number of users in this context.
     *
     * @return the number of users currently in this context.
     */
    public int userCount() {
        return myUserCount;
    }

    private class UserIterator implements Iterator<User> {
        private Iterator<Deliverer> myInnerIterator;
        private User myNext;
        UserIterator() {
            myInnerIterator = myGroup.members().iterator();
            myNext = getNextUser();
        }
        public boolean hasNext() {
            return myNext != null;
        }
        public User next() {
            if (myNext != null) {
                User result = myNext;
                myNext = getNextUser();
                return result;
            } else {
                throw new NoSuchElementException();
            }
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
        private User getNextUser() {
            while (myInnerIterator.hasNext()) {
                Deliverer next = myInnerIterator.next();
                if (next instanceof User) {
                    return (User) next;
                }
            }
            return null;
        }
    }

    /**
     * Obtain an iterator over the users currently in this context.  Note that
     * this iterator is only valid within a single turn.
     *
     * @return an @{link java.util.Iterator} over this context's current users.
     */
    public Iterator<User> userIterator() {
        return new UserIterator();
    }

    /**
     * Handle the 'exit' verb.
     *
     * Exit the context and disconnect the user who sent it.
     */
    @JSONMethod
    public void exit(Deliverer from) throws MessageHandlerException {
        User fromUser = (User) from;
        fromUser.exitContext("normal exit", "bye", false);
    }

    /* ----- BasicObject overrides ----------------------------------------- */

    /**
     * Obtain the context this object is associated with.
     *
     * @return the context itself.
     */
    public Context context() {
        return this;
    }

    /**
     * Test if this object is a container.  (Note: in this case, it is.)
     *
     * @return true -- all contexts are containers.
     */
    public boolean isContainer() {
        return true;
    }

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case,
     *    "context".
     */
    String type() {
        return "context";
    }

    /**
     * Obtain the user this object is currently contained by.
     *
     * @return null, since a context is never contained by a user.
     */
    public User user() {
        return null;
    }

    /* ----- Deliverer interface ------------------------------------------- */

    /**
     * Send a message to everyone in this context.  The message will be
     * delivered to every client whose user is currently in the context.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        myGroup.send(message);
    }

    /* ----- Encodable. interface, inherited from BasicObject -------------- */

    /**
     * Encode this context for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this context.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("context", control);
        if (control.toClient()) {
            result.addParameter("ref", myRef);
        } else {
            result.addParameter("capacity", myMaxCapacity);
            if (myBaseCapacity != -1) {
                result.addParameter("basecapacity", myBaseCapacity);
            }
            if (amSemiPrivate) {
                result.addParameter("semiprivate", amSemiPrivate);
            }
            if (amEntryRestricted) {
                result.addParameter("restricted", amEntryRestricted);
            }
            if (amContentAgnostic) {
                result.addParameter("agnostic", amContentAgnostic);
            }
            if (amAllowableTemplate) {
                result.addParameter("template", true);
            }
            if (amMandatoryTemplate) {
                result.addParameter("templateonly", true);
            }
            if (myUsers == null) {
                result.addParameter("multientry", true);
            }
            if (mySubscriptions != null) {
                result.addParameter("subscribe", mySubscriptions);
            }
        }
        result.addParameter("name", myName);
        if (myModSet != null) {
            JSONLiteralArray mods = myModSet.encode(control);
            if (mods.size() > 0) {
                result.addParameter("mods", mods);
            }
        }
        if (control.toRepository() && myUserMods != null) {
            JSONLiteralArray userMods = new JSONLiteralArray(control);
            for (Mod mod : myUserMods) {
                userMods.addElement(mod);
            }
            if (userMods.size() > 0) {
                result.addParameter("usermods", userMods);
            }
        }
        result.finish();
        return result;
    }
}
