package org.elkoserver.server.director;

import java.security.SecureRandom;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

/**
 * Singleton handler for the director 'user' protocol.
 *
 * The 'user' protocol consists of one request:
 *
 *   'reserve' - Requests a reservation on the user's behalf for entry into a
 *      particular context.
 */
class UserHandler extends BasicProtocolHandler {
    /** Random number generator, for reservations. */
    static private SecureRandom theRandom = new SecureRandom();

    /** The director for this handler. */
    private Director myDirector;

    /**
     * Constructor.
     *
     * @param director  The director object for this handler.
     */
    UserHandler(Director director) {
        myDirector = director;
    }

    /**
     * Obtain the director object for this server.
     *
     * @return the director object this handler is handling for.
     */
    Director director() {
        return myDirector;
    }  

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'director'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "director";
    }

    /**
     * Handle the 'reserve' verb.
     *
     * User requests a reservation with a provider server.
     *
     * @param from  The user asking for the reservation.
     * @param protocol  The protocol it wants to use.
     * @param contextName  The context it is seeking.
     * @param user  The user who is asking for this.
     * @param tag  Optional tag for requestor to match
     */
    @JSONMethod({ "protocol", "context", "user", "tag" })
    public void reserve(DirectorActor from, String protocol,
                        String contextName, OptString user, OptString optTag)
        throws MessageHandlerException
    {
        from.ensureAuthorizedUser();

        String userName = user.value(null);
        Provider provider = null;
        String tag = optTag.value(null);

        /* See if somebody is serving the requested context. */
        OpenContext context = myDirector.getContext(contextName);

        /* If nobody is serving it, look for somebody serving a clone. */
        if (context == null) {
            for (OpenContext clone : myDirector.contextClones(contextName)) {
                if (!clone.isFullClone() && !clone.provider().isFull() &&
                        !clone.gateIsClosed()) {
                    context = clone;
                    contextName = clone.name();
                    break;
                }
            }
        }

        if (context == null) {
            /* If nobody is serving it, pick a provider to start it up. */
            provider = myDirector.locateProvider(contextName, protocol,
                                                 from.isInternal());
            if (provider == null) {
                from.send(msgReserve(this, contextName, userName, null, null,
                                     "unable to find suitable server", tag));
                return;
            }
        } else {
            /* If somebody is serving it, make sure it's really usable. */
            if (context.isRestricted() && !from.isInternal()) {
                from.send(msgReserve(this, contextName, userName, null, null,
                                     "unable to find suitable server", tag));
                return;
            } else if (context.gateIsClosed()) {
                from.send(msgReserve(this, contextName, userName, null, null,
                                     context.gateClosedReason(), tag));
                return;
            } else if (context.isFull()) {
                from.send(msgReserve(this, contextName, userName, null, null,
                                     "requested context full", tag));
                return;
            } else if (context.provider().isFull()) {
                from.send(msgReserve(this, contextName, userName, null, null,
                                     "server full", tag));
                return;
            } else {
                provider = context.provider();
            }
        }
        
        String hostPort = provider.hostPort(protocol);
        if (hostPort != null) {
            /* Issue reservation to provider and user. */
            String reservation = "" + Math.abs(theRandom.nextLong());
            provider.actor().send(msgDoReserve(myDirector.providerHandler(),
                                               contextName, userName,
                                               reservation));
            from.send(msgReserve(this, contextName, userName, hostPort,
                                 reservation, null, tag));
        } else {
            /* Sorry dude, no can do. */
            from.send(msgReserve(this, contextName, userName, null, null,
                                 "requested protocol not available", tag));
        }
    }

    static JSONLiteral msgDoReserve(Referenceable target, String context,
        String user, String reservation)
    {
        JSONLiteral msg = new JSONLiteral(target, "doreserve");
        msg.addParameter("context", context);
        msg.addParameterOpt("user", user);
        msg.addParameterOpt("reservation", reservation);
        msg.finish();
        return msg;
    }

    static JSONLiteral msgReserve(Referenceable target, String context,
        String user, String hostPort, String reservation, String deny,
        String tag)
    {
        JSONLiteral msg = new JSONLiteral(target, "reserve");
        msg.addParameter("context", context);
        msg.addParameterOpt("user", user);
        msg.addParameterOpt("hostport", hostPort);
        msg.addParameterOpt("reservation", reservation);
        msg.addParameterOpt("deny", deny);
        msg.addParameterOpt("tag", tag);
        msg.finish();
        return msg;
    }
}
