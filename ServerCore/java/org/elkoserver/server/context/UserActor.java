package org.elkoserver.server.context;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.SourceRetargeter;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a connection to a user in one or more contexts.
 */
class UserActor
    extends RoutingActor
    implements SourceRetargeter, BasicProtocolActor
{
    /** The users this actor is the actor for, by context. */
    private Map<Context, User> myUsers;

    /** Counter for assigning emphemeral user IDs. */
    static private int theNextTempID = 1;

    /** Flag to prevent race in exit where users bump off themselves. */
    private boolean amDead;

    /** Timeout for kicking off users who connect and don't enter a context. */
    private Timeout myEntryTimeout;

    /** True if reservations are required. */
    private boolean amAuthRequired;

    /** Protocol being spoken on this actor's connection. */
    private String myProtocol;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Connection associated with this user. */
    private Connection myConnection;

    /** The contextor for this server. */
    private Contextor myContextor;

    /**
     * Constructor.
     *
     * @param connection  Connection associated with this user.
     * @param contextor  The contextor for this server.
     * @param authRequired  True if this use needs to tender a reservation in
     *    order to enter.
     * @param protocol  Protocol being used on the new connection
     * @param appTrace  Trace object for diagnostics.
     */
    UserActor(Connection connection, Contextor contextor, boolean authRequired,
              String protocol, Trace appTrace)
    {
        super(connection, contextor);
        myConnection = connection;
        myContextor = contextor;
        tr = appTrace;
        amDead = false;

        myUsers = new HashMap<Context, User>();
        amAuthRequired = authRequired;
        myProtocol = protocol;
        startEntryTimeout();
    }

    /**
     * Evict and disconnect the user before they're even in.
     *
     * @param why  Explanation string to send them as the last thing they see
     *    from before the connection drops.
     * @param whyCode  Machine readable code tag version of 'why'
     */
    private void abruptExit(String why, String whyCode) {
        send(Msg.msgExit(myContextor.session(), why, whyCode, false));
        tr.eventm("abrupt exit: " + why);
        if (!amDead && myUsers.isEmpty()) {
            tr.eventm("abrupt exit disconnects pre-entry user");
            amDead = true;
            close();
        }
    }

    /**
     * Handle loss of connection from the user.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(final Connection connection,
                               final Throwable reason)
    {
        if (!amDead) {
            amDead = true;
            final List<User> users = new LinkedList<User>(myUsers.values());
            myContextor.server().enqueue(new Runnable() {
                    public void run() {
                        for (User user : users) {
                            user.connectionDied(connection, reason);
                        }
                    }
                });
            close();
        }
    }

    /**
     * Authorize (or refuse authorization for) a connection for this actor.
     * In the case of a UserActor, we don't participate in the authorization
     * abstraction at all, so we always just say no.
     *
     * @param handler  Handler requesting the authorization.
     * @param auth  Authorization information from the authorization request
     *    message, or null if no authorization information was provided.
     * @param label  Label (e.g., displayable user name) string from the
     *    authorization request message, or "&lt;anonymous&gt;" if no value was
     *    specified.
     *
     * @return true if the authorization succeeded and the session should be
     *    allowed to proceed, false if it did not and the session should be
     *    disconnected.
     */
    public boolean doAuth(BasicProtocolHandler handler, AuthDesc auth,
                          String label)
    {
        return false;
    }

    /**
     * Obtain this user actors's connection ID.
     *
     * @return the ID number of the connection associated with this actor.
     */
    public int connectionID() {
        return myConnection.id();
    }

    /**
     * Disconnect this actor.
     */
    public void doDisconnect() {
        connectionDied(myConnection, new Exception("user disconnect"));
    }

    /**
     * Enter the user into a context.
     *
     * The user connection will be dropped if: (1) the user reference they ask
     * for is already in use, (2) it is an invalid user reference, (3) the
     * context they are asking for doesn't exist, (4) the server is full, or
     * (5) the connection they are coming in over requires reservations to be
     * used and they haven't provided a valid reservation authorization code.
     *
     * @param userRef  The user ID they claim, or null if none was asserted.
     * @param name  The name they want to have, or null if none was asserted.
     * @param contextRef  The context they want to enter.
     * @param contextTemplate  Optional reference the template context from
     *    which the context should be derived.
     * @param sess  Client session ID for the connection to the context, or
     *    null if the client doesn't care.
     * @param auth  Optional authorization code for entry, if needed.
     * @param utag  Constructor tag string for synthetic user, or null if none
     * @param uparam  Arbitrary object parameterizing synthetic user, or null
     * @param debug  This session will use debug settings, if enabled.
     * @param scope  Application scope for filtering mods
     */
    void enterContext(String userRef, String name, String contextRef,
                      String contextTemplate, String sess, String auth,
                      String utag, JSONObject uparam, boolean debug,
                      String scope)
    {
        tr.eventi("attempting to enter context " + contextRef);
        if (myEntryTimeout != null) {
            myEntryTimeout.cancel();
            myEntryTimeout = null;
        }
        if (debug) {
            myConnection.setDebugMode(true);
        }

        if (myContextor.limit() > 0 &&
                myContextor.userCount() >= myContextor.limit()) {
            abruptExit("server full", "full");
            return;
        }

        boolean isEphemeral = false;
        boolean isAnonymous = false;
        if (utag != null) {
            userRef = null;
            isAnonymous = false;
            if (name == null) {
                name = "_synth_" + (theNextTempID++);
            }
        } else if (userRef == null) {
            userRef = "user-anon";
            isEphemeral = true;
            isAnonymous = true;
            if (name == null) {
                name = "_anon_" + (theNextTempID++);
            }
        }
        DirectorActor opener = null;
        if (amAuthRequired) {
            if (auth == null) {
                abruptExit("reservation required", "nores");
                return;
            } else {
                Reservation reservation;
                if (isEphemeral) {
                    reservation =
                        myContextor.lookupReservation(null, contextRef, auth);
                } else {
                    reservation =
                        myContextor.lookupReservation(userRef, contextRef,
                                                      auth);
                }
                if (reservation == null) {
                    abruptExit("invalid reservation", "badres");
                    return;
                }
                opener = reservation.issuer();
                reservation.redeem();
            }
        }

        EnterRunnable runnable =
            new EnterRunnable(userRef, isEphemeral, isAnonymous, name,
                              contextRef, sess);
        if (utag != null) {
            myContextor.synthesizeUser(myConnection, utag, uparam, contextRef,
                                       contextTemplate, scope, runnable);
        } else {
            myContextor.loadUser(userRef, scope, runnable);
        }
        myContextor.getOrLoadContext(contextRef, contextTemplate, runnable,
                                     opener);
    }

    /**
     * Runnable to handle asynchronous database retrieval of relevant objects
     * needed to process an enter request.  This will be invoked twice: once
     * for the user object and once for the context object.  The order in which
     * the two objects are delivered is not important.  On the second
     * invocation it checks to see if both objects were successfully delivered
     * by the database: If so, it processes the entry normally.  If not, the
     * user is kicked off.
     */
    private class EnterRunnable implements ArgRunnable {
        private String myUserRef;
        private boolean amEphemeral;
        private boolean amAnonymous;
        private String myEntryName;
        private String myContextRef;
        private String mySess;
        private User myUser;
        private Context myContext;
        private int myComponentCount;

        EnterRunnable(String userRef, boolean isEphemeral, boolean isAnonymous,
                      String entryName, String contextRef, String sess)
        {
            myUserRef = userRef;
            amEphemeral = isEphemeral;
            amAnonymous = isAnonymous;
            myEntryName = entryName;
            myContextRef = contextRef;
            mySess = sess;
            myUser = null;
            myContext = null;
            myComponentCount = 0;
        }

        public void run(Object obj) {
            if (amDead) {
                /* User disconnected before getting all the way in. */
                return;
            }
            ++myComponentCount;
            if (obj instanceof User) {
                myUser = (User) obj;
            } else if (obj instanceof Context) {
                myContext = (Context) obj;
            }
            if (myComponentCount == 2) {
                if (myUser == null) {
                    abruptExit("invalid user " + myUserRef, "baduser");
                } else if (myContext == null) {
                    abruptExit("invalid context " + myContextRef,"badcontext");
                } else {
                    if (myUserRef == null) {
                        myUserRef = myUser.ref();
                    }
                    if (myUserRef == null) {
                        myUserRef = myContextor.uniqueID("u");
                    }
                    String name = myUser.name();
                    if (name == null) {
                        name = myEntryName;
                    }
                    String subID = myContextor.uniqueID("");
                    String ref = myUserRef + subID;

                    myUsers.put(myContext, myUser);
                    myUser.activate(ref, subID, myContextor, name, mySess,
                                    amEphemeral, amAnonymous, UserActor.this,
                                    tr);
                    myUser.checkpoint();
                    String problem = myUser.enterContext(myContext);
                    myContextor.noteUser(myUser, true);
                    if (problem != null) {
                        myUser.exitContext(problem, problem, false);
                    }
                }
            }
        }
    }

    /**
     * Remove this actor from one of the contexts that it is in.
     */
    void exitContext(Context context) {
        if (context != null) {
            myUsers.remove(context);
        }
        if (!amDead && myUsers.isEmpty()) {
            startEntryTimeout();
        }
    }

    /**
     * Return the the object that should be the treated as the source of a
     * message.  In the case of a UserActor, this is the User object.
     *
     * @param target  The object to which the message is addressed.
     *
     * @return an object that should be presented to the message handler as the
     *    source of a message to 'target' in place of this object.
     */
    public Deliverer findEffectiveSource(DispatchTarget target) {
        if (target instanceof BasicObject) {
            Context context = ((BasicObject) target).context();
            return myUsers.get(context);
        } else {
            return this;
        }
    }

    /**
     * Get the protocol associated with this actor's connection.
     *
     * @return a string labeling this actor's connection's protocol.
     */
    String protocol() {
        return myProtocol;
    }

    /**
     * Initiate a timeout waiting for the user to enter a context.  If the
     * timeout trips before the user acts, the user will be disconnected.
     */
    private void startEntryTimeout() {
        myEntryTimeout =
            Timer.theTimer().after(
                myContextor.entryTimeout(),
                new TimeoutNoticer() {
                    public void noticeTimeout() {
                        if (myEntryTimeout != null) {
                            myEntryTimeout = null;
                            abruptExit("entry timeout", "timeout");
                        }
                    }
                });
    }

    /**
     * Get the user associated with this actor in some context.
     *
     * @param context  The context with which the caller is concerned.
     *
     * @return the User associated with this actor in the given context.
     */
    User user(Context context) {
        return myUsers.get(context);
    }
}
