package org.elkoserver.server.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a connection to a director.
 */
class DirectorActor extends NonRoutingActor {
    /** Send group containing all the director connections. */
    private DirectorGroup myGroup;

    /** Map from tag strings to users awaiting reservations. */
    private Map<String, User> myPendingReservationRequests;

    /** Counter for generating tag strings for new reservation requests. */
    private int myNextReservationTag;

    /** How long to wait for a director to give us a reservation. */
    private static final int INTERNAL_RESERVATION_TIMEOUT = 10 * 1000;

    /**
     * Constructor.
     *
     * @param connection  The connection for actually communicating.
     * @param dispatcher  Message dispatcher for incoming messages.
     * @param group  The send group for all the directors.
     * @param host  Host description for this connection.
     */
    DirectorActor(Connection connection, MessageDispatcher dispatcher,
                  DirectorGroup group, HostDesc host) {
        super(connection, dispatcher);
        myGroup = group;
        myPendingReservationRequests = new HashMap<String, User>();
        myNextReservationTag = 1;
        group.admitMember(this);
        send(msgAuth(this, host.auth(), group.contextor().serverName()));
        for (HostDesc listener : group.listeners()) {
            if ("reservation".equals(listener.auth().mode())) {
                send(msgAddress(this, listener.protocol(),
                                listener.hostPort()));
            }
        }
        for (String family : group.contextor().contextFamilies()) {
            if (family.startsWith("$")) {
                send(msgWillserve(this, family.substring(1), group.capacity(),
                                  true));
            } else {
                send(msgWillserve(this, family, group.capacity(), false));
            }
        }
    }

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        Trace.comm.eventm("lost director connection " + connection + ": " +
                          reason);
        myGroup.expelMember(this);
    }

    /**
     * Special iterator to iterate through all of a context's clones or all of
     * a user's clones or the intersection of a set of a user clones and a set
     * of context clones.
     *
     * @internal Note that this class has the form of Iterator but it does not
     * actually implement the Iterator interface because some of its methods
     * need to declare exceptions.
     */
    private class RelayIterator {
        private int myMode;
        private final static int MODE_CONTEXT = 1;
        private final static int MODE_USER = 2;
        private final static int MODE_USER_IN_CONTEXT = 3;
        private Iterator<DispatchTarget> myContexts;
        private Iterator<DispatchTarget> myUsers;
        private String myContextRef;
        private String myUserRef;
        private Context myActiveContext;
        private Object myNextResult;

        /**
         * Constructor.
         *
         * @param context  Optional context reference specifying the context
         *    of interest (omitted if only iterating over users).
         * @param user  Optional user reference specifying the user of
         *    interest (omitted if only iterating over contexts).
         */
        RelayIterator(OptString context, OptString user)
            throws MessageHandlerException
        {
            myNextResult = null;
            myActiveContext = null;
            myContextRef = context.value(null);
            myUserRef = user.value(null);

            myContexts = lookupClones(myContextRef).iterator();
            myUsers = lookupClones(myUserRef).iterator();

            if (myContexts.hasNext()) {
                if (myUsers.hasNext()) {
                    myMode = MODE_USER_IN_CONTEXT;
                    myActiveContext = nextContext();
                } else {
                    myMode = MODE_CONTEXT;
                }
            } else {
                if (myUsers.hasNext()) {
                    myMode = MODE_USER;
                } else {
                    throw new MessageHandlerException(
                        "missing context and/or user parameters");
                }
            }
        }

        boolean hasNext() throws MessageHandlerException {
            if (myNextResult == null) {
                try {
                    myNextResult = next();
                    return true;
                } catch (NoSuchElementException e) {
                    return false;
                }
            } else {
                return true;
            }
        }
        
        private List<DispatchTarget> lookupClones(String ref)
            throws MessageHandlerException
        {
            if (ref == null) {
                return Collections.emptyList();
            } else {
                List<DispatchTarget> result = myGroup.contextor().clones(ref);
                if (result.isEmpty()) {
                    throw new MessageHandlerException(ref + " not found");
                } else {
                    return result;
                }
            }
        }

        private User nextUser() throws MessageHandlerException {
            User user;
            try {
                user = (User) myUsers.next();
            } catch (ClassCastException e) {
                throw new MessageHandlerException("user reference " +
                    myUserRef + " does not refer to a user");
            }
            return user;
        }

        private Context nextContext() throws MessageHandlerException {
            Context context;
            try {
                context = (Context) myContexts.next();
            } catch (ClassCastException e) {
                throw new MessageHandlerException("context reference " +
                    myContextRef + " does not refer to a context");
            }
            return context;
        }

        Object next() throws MessageHandlerException {
            if (myNextResult != null) {
                Object result = myNextResult;
                myNextResult = null;
                return result;
            }
            switch (myMode) {
            case MODE_CONTEXT:
                return nextContext();

            case MODE_USER:
                return nextUser();

            case MODE_USER_IN_CONTEXT:
                while (true) {
                    while (myUsers.hasNext()) {
                        User user = nextUser();
                        if (user.context() == myActiveContext) {
                            return user;
                        }
                    }
                    myActiveContext = nextContext();
                    myUsers = lookupClones(myUserRef).iterator();
                }

            default:
                throw new Error("internal error: invalid mode " + myMode);
            }
        }
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "provider";
    }

    /**
     * Remove an expired or redeemed reservation from the reservation table.
     *
     * @param reservation  The reservation to remove.
     */
    void removeReservation(Reservation reservation) {
        myGroup.removeReservation(reservation);
    }

    /**
     * Handle the 'close' verb.
     *
     * Process a directive to close a context or evict a user.
     *
     * @param context  The context to close, or omitted if not relevant.
     * @param user  The user to evict, or omitted if not relevant.
     * @param dup  True if this is being done to eliminated a duplicate
     *    context.
     */
    @JSONMethod({ "context", "user", "dup" })
    public void close(DirectorActor from, OptString context, OptString user,
                      OptBoolean dup)
        throws MessageHandlerException
    {
        boolean isDup = dup.value(false);

        /* Must copy lists of dead users and contexts to avert
           ConcurrentModificationException from user.exitContext() and
           context.forceClose(). */
        List<User> deadUsers = new LinkedList<User>();
        List<Context> deadContexts = new LinkedList<Context>();
        RelayIterator relayIter = new RelayIterator(context, user);
        while (relayIter.hasNext()) {
            Object obj = relayIter.next();
            if (obj instanceof Context) {
                deadContexts.add((Context) obj);
            } else /* if (obj instanceof User) */ {
                deadUsers.add((User) obj);
            }
        }
        for (User deaduser : deadUsers) {
            deaduser.exitContext("admin", "admin", isDup);
        }
        for (Context deadContext : deadContexts) {
            deadContext.forceClose(isDup);
        }
    }

    /**
     * Handle the 'doreserve' verb.
     *
     * Process an entry reservation for a user about to enter a context.
     *
     * @param context  The context to be entered.
     * @param user  The user who will enter.
     * @param reservation  Authorization code for entry.
     */
    @JSONMethod({ "context", "user", "reservation" })
    public void doreserve(DirectorActor from, String context, OptString user,
                          String reservation)
    {
        myGroup.addReservation(new Reservation(user.value(null), context,
            reservation, myGroup.reservationTimeout(), from));
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Process a directive to reinitialize relationships with directors.
     */
    @JSONMethod
    public void reinit(DirectorActor from) {
        myGroup.contextor().reinitServer();
    }

    /**
     * Handle the 'relay' verb.
     *
     * Relay a message to various entities on this server.
     */
    @JSONMethod({ "context", "user", "msg" })
    public void relay(DirectorActor from, OptString context, OptString user,
                      JSONObject msg)
        throws MessageHandlerException
    {
        RelayIterator iter = new RelayIterator(context, user);
        while (iter.hasNext()) {
            myGroup.contextor().deliverMessage((BasicObject) iter.next(), msg);
        }
    }

    void pushNewContext(User who, String contextRef) {
        final String tag = "" + myNextReservationTag++;
        myPendingReservationRequests.put(tag, who);
        Timer.theTimer().after(
            INTERNAL_RESERVATION_TIMEOUT,
            new TimeoutNoticer() {
                public void noticeTimeout() {
                    User user = myPendingReservationRequests.remove(tag);
                    if (user != null) {
                        user.exitContext("no response", "badres", false);
                    }
                }
            });
        send(msgReserve(this, who.protocol(), contextRef, who.baseRef(), tag));
    }

    /**
     * Handle the 'reserve' verb.
     *
     * Process a reply to a reservation request issued from here.
     *
     * @param from  The user asking for the reservation.
     * @param protocol  The protocol it wants to use.
     * @param contextName  The context it is seeking.
     * @param user  The user who is asking for this.
     * @param tag  Optional tag for requestor to match
     */
    @JSONMethod({ "context", "user", "hostport", "reservation", "deny", "tag"})
    public void reserve(DirectorActor from, String context, OptString optUser,
                        OptString optHostPort, OptString optReservation,
                        OptString optDeny, OptString optTag)
        throws MessageHandlerException
    {
        String tag = optTag.value(null);
        String hostPort = optHostPort.value(null);
        String reservation = optReservation.value(null);
        String deny = optDeny.value(null);
        if (tag != null) {
            User who = myPendingReservationRequests.remove(tag);
            if (who == null) {
                Trace.comm.warningi("received reservation for unknown tag " +
                                    tag);
            } else if (deny != null) {
                who.exitContext(deny, "dirdeny", false);
            } else if (hostPort == null) {
                who.exitContext("no hostport for next context", "dirfail",
                                false);
            } else if (reservation == null) {
                who.exitContext("no reservation for next context", "dirfail",
                                false);
            } else {
                who.exitWithContextChange(context, hostPort, reservation);
            }
        } else {
            Trace.comm.warningi("received reservation reply without tag");
        }
    }

    /**
     * Handle the 'say' verb.
     *
     * Process a directive to send text to a context or a user.
     *
     * @param contextRef  Context to broadcast to, or omitted if not relevant.
     * @param userRef  The user to send to, or omitted if not relevant.
     * @param text  The message to transmit.
     */
    @JSONMethod({ "context", "user", "text" })
    public void say(DirectorActor from, OptString contextRef,
                    OptString userRef, String text)
        throws MessageHandlerException
    {
        RelayIterator iter = new RelayIterator(contextRef, userRef);
        while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Context) {
                Context context = (Context) obj;
                context.send(Msg.msgSay(context, null, text));
            } else /* if (obj instanceof User) */ {
                User user = (User) obj;
                user.send(Msg.msgSay(user, null, text));
            }
        }
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Process a directive to shut down the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({ "kill" })
    public void shutdown(DirectorActor from, OptBoolean kill) {
        myGroup.contextor().shutdownServer(kill.value(false));
    }

    /**
     * Create an 'address' message.
     *
     * @param target  Object the message is being sent to.
     * @param protocol  The protocol to reach this server.
     * @param hostPort  This server's address, as far as the rest of world is
     *    concerned.
     */
    static public JSONLiteral msgAddress(Referenceable target, String protocol,
                                         String hostPort)
    {
        JSONLiteral msg = new JSONLiteral(target, "address");
        msg.addParameter("protocol", protocol);
        msg.addParameter("hostport", hostPort);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'reserve' message.
     *
     * @param target  Object the message is being sent to.
     * @param protocol  The desired protocol for the reservation
     * @param contextRef  The context the reservation is sought for
     * @param userRef  The user for whom the reservation is sought
     * @param tag  Tag for matching responses with requests
     */
    static public JSONLiteral msgReserve(Referenceable target, String protocol,
                                         String contextRef, String userRef,
                                         String tag)
    {
        JSONLiteral msg = new JSONLiteral(target, "reserve");
        msg.addParameter("protocol", protocol);
        msg.addParameter("context", contextRef);
        msg.addParameterOpt("user", userRef);
        msg.addParameterOpt("tag", tag);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'willserve' message.
     *
     * @param target  Object the message is being sent to.
     * @param context  The context family that will be served.
     * @param capacity  Maximum number of users that will be served.
     * @param restricted  True if the context family is restricted
     */
    static public JSONLiteral msgWillserve(Referenceable target,
                                           String context, int capacity,
                                           boolean restricted)
    {
        JSONLiteral msg = new JSONLiteral(target, "willserve");
        msg.addParameter("context", context);
        if (capacity > 0) {
            msg.addParameter("capacity", capacity);
        }
        if (restricted) {
            msg.addParameter("restricted", restricted);
        }
        msg.finish();
        return msg;
    }
}
