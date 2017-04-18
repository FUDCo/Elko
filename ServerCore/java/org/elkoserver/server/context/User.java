package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * A User represents a connection to someone entered into a context from a
 * client.  It is one of the three basic object types (along with {@link
 * Context} and {@link Item}).
 */
public class User extends BasicObject implements Deliverer { 
    /** True once user has actually been placed in its initial context. */
    private boolean hasArrived;

    /** Flag indicating that user has completed context entry. */
    private boolean amEntered;

    /** Flag to prevent race in exit where users bump off themselves. */
    private boolean amExited;

    /** Context that user is currently in (or null if not in a context). */
    private Context myContext;

    /** Client session ID for this user's connection to the context (null if
        the client isn't concerned with identifying this). */
    private String mySess;

    /* Fields below here only apply to active Users. */

    /** Send group in which this user is currently a member. */
    private SendGroup myGroup;

    /** The actor that represents the connection to the client. */
    private UserActor myActor;

    /** Connection associated with this user. */
    private Connection myConnection;

    /** Optional watcher for friend presence changes. */
    private PresenceWatcher myPresenceWatcher;

    /** Flag that user is an anonymous, ephemeral user. */
    private boolean amAnonymous;

    /** Flag that user contents are opaque to other users. */
    private boolean amPrivateContents;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * JSON-driven constructor.
     *
     * @param name  The name of the user.
     * @param mods  Array of mods to attach to the user; can be null if no mods
     *    are to be attached at initial creation time.
     * @param contents  Array of inactive items that will be the initial
     *    contents of this user, or null if there are no contents now.
     * @param ref  Optional reference string for this user object.
     * @param pos  Optional position of the user within its context.
     */
    @JSONMethod({ "name", "mods", "contents", "ref", "?pos" })
    User(OptString name, Mod mods[], Item contents[], OptString ref,
         Position pos)
    {
        this(name.value(null), mods, contents, ref.value(null), pos);
    }

    /**
     * Direct constructor.
     *
     * @param name  The name of the user.
     * @param mods  Array of mods to attach to the user; can be null if no mods
     *    are to be attached at initial creation time.
     * @param contents  Array of inactive items that will be the initial
     *    contents of this user, or null if there are no contents now.
     * @param pos  Optional position of the user within its context.
     */
    public User(String name, Mod mods[], Item contents[], String ref,
                Position pos)
    {
        super(name, mods, true, contents, pos);
        myRef = ref;
        myContext = null;
        amExited = false;
        amAnonymous = false;
        amPrivateContents = false;
        amEntered = false;
        hasArrived = false;
        mySess = null;
        myPresenceWatcher = null;
        myGroup = LimboGroup.theLimboGroup;
        myGroup.admitMember(this);
    }

    /**
     * Activate a user.
     *
     * @param ref  Reference string identifying this user.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param contextor  The contextor for this server.
     * @param name  The (revised) name for this user.
     * @param sess  Client session ID for this user's connection to their
     *    context, or null if the client doesn't care.
     * @param isEphemeral  True if this user is ephemeral (won't checkpoint).
     * @param isAnonymous  True if this user is anonymous
     * @param actor  The actor through which this user communicates.
     * @param appTrace  Trace object for diagnostics.
     */
    void activate(String ref, String subID, Contextor contextor, String name,
                  String sess, boolean isEphemeral, boolean isAnonymous,
                  UserActor actor, Trace appTrace)
    {
        super.activate(ref, subID, isEphemeral, contextor);
        tr = appTrace;
        if (name != null) {
            myName = name;
        }
        mySess = sess;
        myActor = actor;
        amAnonymous = isAnonymous;
        amEntered = true;
    }

    /**
     * Add a new mod to this user.  The mod must be a {@link UserMod} even
     * though the method is declared generically.  If it is not, it will not be
     * added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be a {@link UserMod}.
     */
    void attachMod(Mod mod) {
        if (mod instanceof UserMod) {
            super.attachMod(mod);
            if (mod instanceof PresenceWatcher) {
                myPresenceWatcher = (PresenceWatcher) mod;
            }
        } else {
            tr.errorm("attempt to attach non-UserMod " + mod + " to " + this);
        }
    }

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    void connectionDied(Connection connection, Throwable reason) {
        disconnect();
        tr.eventm(this + " connection died: " + connection);
    }

    /**
     * Obtain this user's connection ID.
     *
     * @return the ID number of the connection associated with this user.
     */
    public int connectionID() {
        return myActor.connectionID();
    }

    /**
     * Obtain the context this user is currently contained by.
     *
     * @return the context the user is in.
     */
    public Context context() {
        return myContext;
    }

    /**
     * Do the actual work of exiting a user from their context and
     * disconnecting them.
     */
    private void disconnect() {
        if (!amExited) {
            tr.eventm("exiting " + this);
            amExited = true;
            if (amEntered) {
                checkpoint();
                myContextor.remove(this);
                myContextor.noteUser(this, false);
                exitCurrentContext();
            }
        }
    }

    /**
     * Encode this user for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this user.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("user", control);
        if (control.toClient()) {
            result.addParameter("ref", myRef);
        }
        result.addParameter("name", myName);
        result.addParameterOpt("pos", myPosition);
        if (myModSet != null) {
            JSONLiteralArray mods = myModSet.encode(control);
            if (mods.size() > 0) {
                result.addParameter("mods", mods);
            }
        }
        result.finish();
        return result;
    }

    /**
     * Place this user into a context.  The user will be removed from any
     * previous context first.
     *
     * @param context  The context to enter.
     *
     * @return null if successful, or an error message string if not.
     */
    String enterContext(Context context) {
        exitCurrentContext();
        myContext = context;
        String problem = myContext.enterContext(this);
        if (problem == null) {
            hasArrived = true;
            myGroup.expelMember(this);
            myGroup = context.group();
            myGroup.admitMember(this);
            myContext.attachUserMods(this);
            objectIsComplete();
            myContextor.notifyPendingObjectCompletionWatchers();
            sendUserDescription(this, context, true);
            if (!myContext.isSemiPrivate()) {
                sendUserDescription(neighbors(), context, false);
            }
            return null;
        } else {
            return problem;
        }
    }

    /**
     * Test if this user is allowed into an entry restricted context.
     *
     * @param contextRef  Reference string of the context in question.
     *
     * @return true if this user has the key to enter the specified context,
     *    false if not.
     */
    boolean entryEnabled(String contextRef) {
        return testForEntryKey(this, contextRef);
    }

    /**
     * Remove this user from their context.
     *
     * @param why  Explanation string to send to this user as the last thing
     *    they see from the context.
     * @param whyCode  Machine readable tag encoding 'why'.
     * @param reload  true if the client should attempt a reload (i.e.,
     *    immediately try to enter again), false if not.
     */
    public void exitContext(String why, String whyCode, boolean reload) {
        send(Msg.msgExit(myContext, why, whyCode, reload));
        disconnect();
    }

    /**
     * Remove the user from the current context.
     */
    private void exitCurrentContext() {
        if (myContext != null) {
            myGroup.expelMember(this);
            myGroup = LimboGroup.theLimboGroup;
            myContext.exitContext(this);
            if (myModSet != null) {
                myModSet.purgeEphemeralMods();
            }
            myActor.exitContext(myContext);
            myContext = null;
            hasArrived = false;
        }
    }

    /**
     * Tell the user to go to a different context, then disconnect them from
     * this one.
     *
     * @param contextRef  The ref of the context they should go to
     * @param hostPort  Host:port string of the context server for the context
     * @param reservation  Reservation to get them in.
     */
    void exitWithContextChange(final String contextRef, final String hostPort,
                               final String reservation)
    {
        checkpoint(new ArgRunnable() {
            public void run(Object ignored) {
                send(msgPushContext(myContextor.session(), contextRef,
                                    hostPort, reservation));
            }
        });
    }

    /**
     * Force this user to actually disconnect from the server.
     */
    public void forceDisconnect() {
        myActor.doDisconnect();
    }

    /**
     * Indicate whether this user is an anonymous user (and thus immune to
     * the duplicate user rejection logic, as all anonymous users have the
     * same base ref).
     *
     * @return true if this user is anonymous.
     */
    boolean isAnonymous() {
        return amAnonymous;
    }

    /**
     * Indicate whether this user has arrived in its context or not.
     *
     * @return true if this user has arrived in its context.
     */
    boolean isArrived() {
        return hasArrived;
    }

    /**
     * Test if this object is a container.  (Note: in this case, it is).
     *
     * @return true -- all users are containers.
     */
    public boolean isContainer() {
        return true;
    }

    /**
     * Obtain a message deliverer for sending messages to the other users
     * in this user's context.
     *
     * @return a deliverer representing this user's current neighbors.
     */
    public Deliverer neighbors() {
        return new Neighbors(myGroup, this);
    }

    /**
     * Take notice that a user elsewhere has come or gone.
     *
     * @param observerRef  Ref of user who cares (presumably *this* user)
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
    }

    /**
     * Obtain the protocol by which this user is talking to this server.
     *
     * @return the protocol string for this user's connection
     */
    String protocol() {
        return myActor.protocol();
    }

    /**
     * Begin the sequence of events that will push this user to a different
     * context.  This will trigger a reservation request to the director and
     * ultimately a message to the user telling them where to go and how to get
     * in.
     *
     * @param contextRef  The ref of the context to push them to.
     */
    public void pushNewContext(String contextRef) {
        myContextor.pushNewContext(this, contextRef);
    }

    /**
     * Send a message to this user.  The message will be delivered on the
     * client connection this user is currently connected by.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        myActor.send(message);
    }

    /**
     * Transmit a description of this user as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    public void sendObjectDescription(Deliverer to, Referenceable maker) {
        sendUserDescription(to, maker, false);
    }

    /**
     * Transmit a description of this user as a series of 'make' messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     * @param you  If true, user description is being sent to the user being
     *    described.
     */
    public void sendUserDescription(Deliverer to, Referenceable maker,
                                    boolean you)
    {
        to.send(Msg.msgMake(maker, this, null, you, null));
        if (!amPrivateContents || to == this) {
            Contents.sendContentsDescription(to, this, myContents);
        }
        to.send(Msg.msgReady(this));
    }

    /**
     * Get this user's client-provided context session ID.
     *
     * @return the session ID for this user's context.
     */
    String sess() {
        return mySess;
    }

    /**
     * Test if an oject or any of its contents have a context entry key that
     * enables access to a given context.
     *
     * @param obj  The object to test.
     * @param contextRef  The reference string of the context of interest.
     *
     * @return true if 'obj' or any of the objects it contains have an attached
     *    {@link ContextKey} mod that gives access to the context designated by
     *    'contextRef'.
     */
    private boolean testForEntryKey(BasicObject obj, String contextRef) {
        ContextKey key = (ContextKey) obj.getMod(ContextKey.class);
        if (key != null && key.enablesEntry(contextRef)) {
            return true;
        }
        for (Item item : obj.contents()) {
            if (testForEntryKey(item, contextRef)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obtain a printable string representation of this user.
     *
     * @return a printable representation of this user.
     */
    public String toString() {
        return "User '" + ref() + "'";
    }

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case, "user".
     */
    String type() {
        return "user";
    }

    /**
     * Obtain the user this object is currently associated with.
     *
     * @return the user itself.
     */
    public User user() {
        return this;
    }

    /**
     * Create a 'pushcontext' message.
     *
     * @param target  Object the message is being sent to
     * @param contextRef  Ref of the context to which the user is being sent
     * @param hostPort  Host:port of the context server they should use
     * @param reservation  Reservation code to tender to gain entry
     */
    static JSONLiteral msgPushContext(Referenceable target, String contextRef,
                                      String hostPort, String reservation)
    {
        JSONLiteral msg = new JSONLiteral(target, "pushcontext");
        msg.addParameter("context", contextRef);
        msg.addParameterOpt("hostport", hostPort);
        msg.addParameterOpt("reservation", reservation);
        msg.finish();
        return msg;
    }
}
