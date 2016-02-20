package org.elkoserver.server.presence;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.json.JSONObject;

/**
 * Singleton handler for the presence server 'client' protocol.
 *
 * The 'client' protocol consists of these messages:
 *
 *   'user' - Reports that a particular user has arrived or departed from a
 *      context provided by the sender.
 */
class ClientHandler extends BasicProtocolHandler {
    /** The presence server for this handler. */
    private PresenceServer myPresenceServer;

    /**
     * Constructor.
     *
     * @param presenceServer  The presence server object for this handler.
     */
    ClientHandler(PresenceServer presenceServer) {
        myPresenceServer = presenceServer;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'presence'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "presence";
    }

    /**
     * Handle the 'update' verb.
     *
     * Update the state of a domain's social graph object.
     *
     * @param from  The client issuing the update.
     * @param domain  The domain being updated.
     * @param conf  Domain-specific configuration update parameters.
     */
    @JSONMethod({ "domain", "conf" })
    public void update(PresenceActor from, String domain, JSONObject conf)
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        myPresenceServer.updateDomain(domain, conf, from);
    }

    /**
     * Handle the 'user' verb.
     *
     * Note the arrival or departure of an user.
     *
     * @param from  The client announcing the context change.
     * @param context  The context that the user entered or exited.
     * @param user  The user who entered or exited.
     * @param on  true on entry, false on exit.
     * @param userMeta  Optional user metadata.
     * @param contextMeta  Optional context metadata.
     */
    @JSONMethod({ "context", "user", "on", "?umeta", "?cmeta" })
    public void user(PresenceActor from, String context, String user,
                     boolean on, JSONObject userMeta, JSONObject contextMeta)
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        Client client = from.client();
        if (userMeta != null) {
            client.noteUserMetadata(user, userMeta);
        }
        if (contextMeta != null) {
            client.noteContextMetadata(context, contextMeta);
        }
        if (on) {
            client.noteUserEntry(user, context);
        } else {
            client.noteUserExit(user, context);
        }
    }

    /**
     * Handle the 'subscribe' verb.
     *
     * Note a context's interest in presence updates pertaining to particular
     * domains, and begin sending them update traffic.
     *
     * @param context  The context who is interested
     * @param domains   The domains they are interested in
     * @param visible  Flag indicating if presence in the given context is
     *    visible outside the context
     */
    @JSONMethod({ "context", "?domains", "visible" })
    public void subscribe(PresenceActor from, String context, String[] domains,
                          OptBoolean visible)
    {
        if (domains != null) {
            for (String domain : domains) {
                from.client().subscribeToUpdates(context, domain);
            }
        }
        if (visible.value(true)) {
            from.client().noteVisibleContext(context);
        }
    }

    /**
     * Handle the 'unsubscribe' verb.
     *
     * Note a context's cessation of interest in presence updates and stop
     *    sending them update traffic.
     *
     * @param context  The context who is no longer interested
     */
    @JSONMethod({ "context" })
    public void unsubscribe(PresenceActor from, String context)
    {
        from.client().unsubscribeToUpdates(context);
        from.client().noteInvisibleContext(context);
    }
}
