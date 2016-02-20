package org.elkoserver.server.context;

import java.util.List;
import org.elkoserver.foundation.actor.Actor;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Outbound group containing all the connected presence servers.
 */
class PresencerGroup extends OutboundGroup {
    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param contextor  The server contextor.
     * @param presencers  List of HostDesc objects describing presence
     *    servers with whom to register.
     * @param appTrace  Trace object for diagnostics.
     */
    PresencerGroup(Server server, Contextor contextor,
                   List<HostDesc> presencers, Trace appTrace)
    {
        super("conf.presence", server, contextor, presencers, appTrace);
        connectHosts();
    }

    /* ----- required OutboundGroup methods ----- */

    /**
     * Obtain the class of actors in this group (in this case, PresenceActor).
     *
     * @return this group's actor class.
     */
    Class actorClass() {
        return PresencerActor.class;
    }

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth, in this case, "presence".
     *
     * @return this group's label string.
     */
    String label() {
        return "presence";
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
        PresencerActor presencer =
            new PresencerActor(connection, dispatcher, this, host);
        updatePresencer(presencer);
        return presencer;
    }

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to, in this case,
     * "presence-client".
     *
     * @return a broker service string for this group.
     */
    String service() {
        return "presence-client";
    }

    /* ----- PresencerGroup methods ----- */

    /**
     * Tell the presence servers that a context has opened or closed, when
     * relevant.  In the case of an opening, this includes sending the presence
     * servers a list of the presence domains that the context is subscribing
     * to.
     *
     * @param context  The context
     * @param open  true if the context is being opened, false if being closed
     */
    void noteContext(Context context, boolean open) {
        String[] subscriptions = context.subscriptions();
        if (subscriptions != null) {
            if (open) {
                send(msgSubscribe(context.ref(), subscriptions, true));
            } else {
                send(msgUnsubscribe(context.ref()));
            }
        }
    }

    /**
     * Tell the presence servers that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    void noteUser(User user, boolean on) {
        if (user.context().subscriptions() != null) {
            send(msgUser(user.context().ref(), user.baseRef(), on,
                         userMeta(user), contextMeta(user.context())));
        }
    }

    /**
     * Update a newly connected presence server as to what users are present.
     *
     * @param presencer  The presence server to be updated.
     */
    void updatePresencer(PresencerActor presencer) {
        for (User user : contextor().users()) {
            presencer.send(msgUser(user.context().ref(), user.baseRef(), true,
                                   userMeta(user),
                                   contextMeta(user.context())));
        }
    }

    /**
     * Generate a metadata object for a context.  Right now, we only include
     * the name string.
     *
     * @context  The context for which metadata is sought.
     *
     * @return JSON-encoded metadata for the give context.
     */
    private JSONLiteral contextMeta(Context context) {
        JSONLiteral meta = new JSONLiteral();
        meta.addParameter("name", context.name());
        meta.finish();
        return meta;
    }

    /**
     * Generate a metadata object for a user.  Right now, we only include the
     * name string.
     *
     * @user  The user for whom metadata is sought.
     *
     * @return JSON-encoded metadata for the give user.
     */
    private JSONLiteral userMeta(User user) {
        JSONLiteral meta = new JSONLiteral();
        meta.addParameter("name", user.name());
        meta.finish();
        return meta;
    }

    /**
     * Create a "subscribe" message.
     *
     * @param context  The context that is subscribing
     * @param domains  The presence domains being subscribed to
     * @param visible  Flag indicating if presence in the given context is
     *    visible outside the context
     */
    static public JSONLiteral msgSubscribe(String context, String[] domains,
                                           boolean visible)
    {
        JSONLiteral msg = new JSONLiteral("presence", "subscribe");
        msg.addParameter("context", context);
        msg.addParameter("domains", domains);
        msg.addParameter("visible", visible);
        msg.finish();
        return msg;
    }

    /**
     * Create an "unsubscribe" message.
     *
     * @param context  The context that is ceasing to subscribe
     */
    static public JSONLiteral msgUnsubscribe(String context) {
        JSONLiteral msg = new JSONLiteral("presence", "unsubscribe");
        msg.addParameter("context", context);
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
                                      boolean on, JSONLiteral userMeta,
                                      JSONLiteral contextMeta)
    {
        JSONLiteral msg = new JSONLiteral("presence", "user");
        msg.addParameter("context", context);
        msg.addParameter("user", user);
        msg.addParameter("on", on);
        msg.addParameterOpt("umeta", userMeta);
        msg.addParameterOpt("cmeta", contextMeta);
        msg.finish();
        return msg;
    }
}
