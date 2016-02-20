package org.elkoserver.server.gatekeeper;

import java.util.HashMap;
import java.util.Map;
import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a gatekeeper's connection to its director.
 *
 * Since this is a non-routing actor, it implements its message protocol
 * directly.  This protocol consists of one message:
 *
 *   'reserve' - The return result from a reservation request that was
 *      previously sent to the director.
 */
class DirectorActor extends NonRoutingActor {
    /** The factory holding gatekeeper configuration information. */
    private DirectorActorFactory myFactory;

    /** Flag that this connection is still active. */
    private boolean amLive;

    /** Reservation requests that have been issued to the director, the
        responses to which have not yet been received to. */
    private Map<String, ArgRunnable> myPendingReservations;

    /**
     * Constructor.
     *
     * @param connection  The connection for actually communicating.
     * @param dispatcher  Message dispatcher for incoming messages.
     * @param factory  The factory that created this actor.
     * @param director  Host description for the director connected to.
     */
    DirectorActor(Connection connection, MessageDispatcher dispatcher,
                  DirectorActorFactory factory, HostDesc director)
    {
        super(connection, dispatcher);
        amLive = true;
        myPendingReservations = new HashMap<String, ArgRunnable>();
        myFactory = factory;
        factory.setDirector(this);
        send(msgAuth(this, director.auth(),
                     factory.gatekeeper().serverName()));
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
        amLive = false;
        myFactory.setDirector(null);
        for (Map.Entry<String, ArgRunnable> entry :
                 myPendingReservations.entrySet())
        {
            String key = entry.getKey();
            ArgRunnable handler = entry.getValue();
            int sep = key.indexOf('|');
            handler.run(new ReservationResult(key.substring(0, sep),
                                              key.substring(sep + 1),
                                              "director connection lost"));
        }
    }

    /**
     * Issue a request for a reservation to the director.
     *
     * @param protocol  The protocol for the requested reservation.
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle result.
     */
    void requestReservation(String protocol, String context, String actor,
                            ArgRunnable handler) {
        if (!amLive) {
            handler.run(new ReservationResult(context, actor,
                                              "director connection lost"));
        } else {
            String pactor = actor == null ? "" : actor;
            myPendingReservations.put(context + "|" + pactor, handler);
            send(msgReserve(this, protocol, context, actor));
        }
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'director'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "director";
    }

    /**
     * Handle the 'reserve' verb.
     *
     * Process the reservation result that was returned (or the failure).
     *
     * @param context  The context reserved.
     * @param actor  The actor reserved for.
     * @param hostport  The host:port to connect to.
     * @param auth  The authorization code to connect with.
     * @param deny  Error message in the case of failure.
     */
    @JSONMethod({ "context", "user", "hostport", "reservation", "deny" })
    public void reserve(DirectorActor from, String context, OptString optActor,
                        OptString optHostport, OptString optAuth,
                        OptString optDeny)
    {
        String hostport = optHostport.value(null);
        String auth = optAuth.value(null);
        String deny = optDeny.value(null);
        String actor = optActor.value(null);
        String pactor = (actor == null) ? "" : actor;
        
        String contextKey = context;
        ArgRunnable handler;
        do {
            handler = myPendingReservations.remove(contextKey + "|" + pactor);
            if (handler == null) {
                int slash = contextKey.lastIndexOf('-');
                if (slash > -1) {
                    contextKey = contextKey.substring(0, slash);
                } else {
                    contextKey = null;
                }
            }
        } while (handler == null && contextKey != null);

        if (handler == null) {
            Trace.comm.errorm("received unexpected reservation for " +
                              context + " " + pactor);
        } else if (deny == null) {
            handler.run(new ReservationResult(context, actor, hostport, auth));
        } else {
            handler.run(new ReservationResult(context, actor, deny));
        }
    }

    /**
     * Create a 'reserve' message.
     *
     * @param target  Object the message is being sent to.
     * @param protocol  Desired protocol.
     * @param context  Context to enter.
     * @param actor Who wants to enter.
     */
    static public JSONLiteral msgReserve(Referenceable target, String protocol,
                                         String context, String actor)
    {
        JSONLiteral msg = new JSONLiteral(target, "reserve");
        msg.addParameter("protocol", protocol);
        msg.addParameter("context", context);
        msg.addParameterOpt("user", actor);
        msg.finish();
        return msg;
    }
}
