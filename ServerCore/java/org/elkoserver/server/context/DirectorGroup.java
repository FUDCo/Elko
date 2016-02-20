package org.elkoserver.server.context;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.elkoserver.foundation.actor.Actor;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.LoadWatcher;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;

/**
 * Outbound group containing all the connected directors.
 */
class DirectorGroup extends OutboundGroup {
    /** The listeners this server currently has open. */
    private List<HostDesc> myListeners;

    /** Iterator for cycling through arbitrary relays. */
    private Iterator<Deliverer> myDirectorPicker;

    /** Pending reservations. */
    private Map<Reservation, Reservation> myReservations;
    
    /** How long reservations are good for, in milliseconds. */
    private int myReservationTimeout;

    /** Default reservation expiration time, in seconds. */
    private static final int DEFAULT_RESERVATION_EXPIRATION_TIMEOUT = 30;
    
    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param contextor  The server contextor.
     * @param directors  List of HostDesc objects describing directors with
     *    whom to register.
     * @param listeners  List of HostDesc objects describing active
     *    listeners to register with the indicated directors.
     * @param appTrace  Trace object for diagnostics.
     */
    DirectorGroup(Server server, Contextor contextor,
                  List<HostDesc> directors, List<HostDesc> listeners,
                  Trace appTrace)
    {
        super("conf.register", server, contextor, directors, appTrace);

        server.registerLoadWatcher(new LoadWatcher() {
            public void noteLoadSample(double factor) {
                send(msgLoad(factor));
            }
        });

        myListeners = listeners;
        myDirectorPicker = null;
        myReservations = new HashMap<Reservation, Reservation>();

        myReservationTimeout = 1000 *
            server.props().intProperty("conf.context.reservationexpire",
                                       DEFAULT_RESERVATION_EXPIRATION_TIMEOUT);
        connectHosts();
    }

    /* ----- required OutboundGroup methods ----- */

    /**
     * Obtain the class of actors in this group, in this case, DirectorActor.
     *
     * @return this group's actor class.
     */
    Class actorClass() {
        return DirectorActor.class;
    }

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth, in this case, "director".
     *
     * @return this group's label string.
     */
    String label() {
        return "director";
    }

    /**
     * Get an actor object suitable to act on message traffic on a new
     * connection in this group.
     *
     * @param connection  The new connection
     * @param dispatcher   Message dispatcher for the message protocol on the
     *    new connection
     * @param host  Descriptor information for the host the new connection is
     *    connected to
     *
     * @return a new Actor object for use on this new connection
     */
    Actor provideActor(Connection connection, MessageDispatcher dispatcher,
                       HostDesc host)
    {
        DirectorActor director =
            new DirectorActor(connection, dispatcher, this, host);
        updateDirector(director);
        return director;
    }

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to, in this case,
     * "director-provider".
     *
     * @return a broker service string for this group.
     */
    String service() {
        return "director-provider";
    }

    /* ----- DirectorGroup methods ----- */

    /**
     * Add a new, pending reservation to the reservation table.
     *
     * @param reservation  The reservation to add.
     */
    void addReservation(Reservation reservation) {
        myReservations.put(reservation, reservation);
    }

    /**
     * Get the user capacity of this context server.
     *
     * @return the capacity of this server.
     */
    int capacity() {
        return contextor().limit();
    }

    /**
     * Get a read-only view of the list of active listeners.
     *
     * @return a list of the active listeners.
     */
    List<HostDesc> listeners() {
        return Collections.unmodifiableList(myListeners);
    }

    /**
     * Lookup a reservation.
     *
     * @param who  Whose reservation?
     * @param where  For where?
     * @param authCode  The alleged authCode.
     *
     * @return the requested reservation if there is one, or null if not.
     */
    Reservation lookupReservation(String who, String where, String authCode) {
        Reservation key = new Reservation(who, where, authCode);
        return myReservations.get(key);
    }

    /**
     * Tell the directors that a context has been opened or closed.
     *
     * @param context  The context.
     * @param open  true if opened, false if closed.
     */
    void noteContext(Context context, boolean open) {
        DirectorActor opener = context.opener();
        String ref = context.ref();
        int maxCap = context.maxCapacity();
        int baseCap = context.baseCapacity();
        boolean restricted = context.isRestricted();
        if (opener != null) {
            opener.send(msgContext(ref, open, true, maxCap, baseCap,
                                   restricted));
            sendToNeighbors(opener,
                            msgContext(ref, open, false, maxCap, baseCap,
                                       restricted));
        } else {
            send(msgContext(ref, open, false, maxCap, baseCap, restricted));
        }
    }

    /**
     * Tell the directors that a context gate has been opened or closed.
     *
     * @param context  The context whose gate is being opened or closed
     * @param open  Flag indicating open or closed
     * @param reason  Reason for closing the gate
     */
    void noteContextGate(Context context, boolean open, String reason) {
        send(msgGate(context.ref(), open, reason));
    }

    /**
     * Tell the directors that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    void noteUser(User user, boolean on) {
        send(msgUser(user.context().ref(), user.ref(), on));
    }
    
    /**
     * Pick an arbitrary director.  This is done by a simple round-robin.
     */
    private DirectorActor pickADirector() {
        while (true) {
            if (myDirectorPicker == null || !myDirectorPicker.hasNext()) {
                myDirectorPicker = members().iterator();
                if (!myDirectorPicker.hasNext()) {
                    return null;
                }
            }
            try {
                return (DirectorActor) myDirectorPicker.next();
            } catch (ConcurrentModificationException e) {
                myDirectorPicker = null;
            }
        }
    }

    /**
     * Push a user to a different context: obtain a reservation for the new
     * context from one of our directors, send it to the user, and then kick
     * them out.
     *
     * @param who  The user being pushed
     * @param contextRef  The ref of the context to push them to.
     */
    void pushNewContext(User who, String contextRef) {
        DirectorActor director = pickADirector();
        if (director != null) {
            director.pushNewContext(who, contextRef);
        } else {
            who.exitContext("no director for context push", "nodir", false);
        }
    }

    /**
     * Relay a message to other context servers via a director.
     *
     * @param target  The target of the message.
     * @param contextRef  Contexts to deliver to, or null if don't care.
     * @param userRef  Users to deliver to, or null if don't care.
     * @param message  The message to relay.
     */
    void relay(String target, String contextRef, String userRef,
               JSONLiteral message)
    {
        DirectorActor relayer = pickADirector();
        /* If relayer is null, assume there are no directors and thus no
           relaying to be done. */
        if (relayer != null) {
            relayer.send(msgRelay("provider", contextRef, userRef, message));
        }
    }

    /**
     * Remove an expired or redeemed reservation from the reservation table.
     *
     * @param reservation  The reservation to remove.
     */
    void removeReservation(Reservation reservation) {
        myReservations.remove(reservation);
    }

    /**
     * Obtain the reservation expiration timeout.
     *
     * @return the reservation expiration timeout interval, in milliseconds.
     */
    int reservationTimeout() {
        return myReservationTimeout;
    }

    /**
     * Update a newly connected director as to what contexts and users are
     * open.
     *
     * @param director  The director to be updated.
     */
    void updateDirector(DirectorActor director) {
        for (Context context : contextor().contexts()) {
            director.send(msgContext(context.ref(), true, false,
                                     context.maxCapacity(),
                                     context.baseCapacity(),
                                     context.isRestricted()));
            if (context.gateIsClosed()) {
                director.send(msgGate(context.ref(), true,
                                      context.gateClosedReason()));
            }
        }
        for (User user : contextor().users()) {
            director.send(msgUser(user.context().ref(), user.ref(), true));
        }
    }

    /**
     * Create a "context" message.
     *
     * @param context  The context opened or closed.
     * @param open  Flag indicating open or closed.
     * @param yours  Flag indicating if recipient was controlling director.
     * @param maxCapacity   Max # users in context, or -1 for no limit.
     * @param baseCapacity   Max # users before cloning, or -1 for no limit.
     * @param restricted  Flag indicated if context is restricted
     */
    static public JSONLiteral msgContext(String context, boolean open,
        boolean yours, int maxCapacity, int baseCapacity, boolean restricted)
    {
        JSONLiteral msg = new JSONLiteral("provider", "context");
        msg.addParameter("context", context);
        msg.addParameter("open", open);
        msg.addParameter("yours", yours);
        if (open) {
            if (maxCapacity != -1) {
                msg.addParameter("maxcap", maxCapacity);
            }
            if (baseCapacity != -1) {
                msg.addParameter("basecap", baseCapacity);
            }
            if (restricted) {
                msg.addParameter("restricted", restricted);
            }
        }
        msg.finish();
        return msg;
    }

    /**
     * Create a "load" message.
     *
     * @param factor  Load factor to report.
     */
    static public JSONLiteral msgLoad(double factor) {
        JSONLiteral msg = new JSONLiteral("provider", "load");
        msg.addParameter("factor", factor);
        msg.finish();
        return msg;
    }

    /**
     * Create a "relay" message.
     *
     * @param target  The message target.
     * @param contextName  The base name of the context to relay to.
     * @param userName  The base name of the user to relay to.
     * @param relay  The message to relay.
     */
    static public JSONLiteral msgRelay(String target,
        String contextName, String userName, JSONLiteral relay)
    {
        JSONLiteral msg = new JSONLiteral(target, "relay");
        msg.addParameterOpt("context", contextName);
        msg.addParameterOpt("user", userName);
        msg.addParameter("msg", relay);
        msg.finish();
        return msg;
    }

    /**
     * Create a "gate" message.
     *
     * @param context  The context whose gate is being indicated
     * @param open  Flag indicating open or closed
     * @param reason  Reason for closing the gate
     */
    static public JSONLiteral msgGate(String context, boolean open,
                                      String reason)
    {
        JSONLiteral msg = new JSONLiteral("provider", "gate");
        msg.addParameter("context", context);
        msg.addParameter("open", open);
        msg.addParameterOpt("reason", reason);
        msg.finish();
        return msg;
    }

    /**
     * Create a "user" message.
     *
     * @param context  The context entered or exited.
     * @param user  Who entered or exited.
     * @param on  Flag indicating online or offline.
     */
    static public JSONLiteral msgUser(String context, String user,
                                       boolean on)
    {
        JSONLiteral msg = new JSONLiteral("provider", "user");
        msg.addParameter("context", context);
        msg.addParameter("user", user);
        msg.addParameter("on", on);
        msg.finish();
        return msg;
    }
}
