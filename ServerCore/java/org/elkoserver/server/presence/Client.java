package org.elkoserver.server.presence;

import org.elkoserver.json.JSONObject;

/**
 * The client facet of a presence server actor.  This object represents the
 * state and functionality required when a connected entity is engaging in the
 * client protocol.
 */
class Client {
    /* The presence server itself. */
    PresenceServer myPresenceServer;

    /** The actor through whom this facet communicates. */
    private PresenceActor myActor;

    /**
     * Constructor.
     *
     * @param presenceServer  The presence server whose client this is.
     * @param actor  The actor associated with the client.
     */
    Client(PresenceServer presenceServer, PresenceActor actor) {
        myPresenceServer = presenceServer;
        myActor = actor;
    }

    /**
     * Clean up when the client actor disconnects.
     */
    void doDisconnect() {
    }

    /**
     * Take note of user metadata.
     *
     * @param userRef  The user to whom the metadata applies
     * @param userMeta  The user metadata itself.
     */
    void noteUserMetadata(String userRef, JSONObject userMeta) {
        myPresenceServer.noteUserMetadata(userRef, userMeta);
    }

    /**
     * Take note of context metadata.
     *
     * @param contextRef  The context to which the metadata applies
     * @param contextMeta  The context metadata itself.
     */
    void noteContextMetadata(String contextRef, JSONObject contextMeta) {
        myPresenceServer.noteContextMetadata(contextRef, contextMeta);
    }

    /**
     * Take note that a user has entered one of this client's contexts.
     *
     * @param userName  The name of the user who entered.
     * @param contextName  The name of the context they entered.
     */
    void noteUserEntry(String userName, String contextName) {
        myPresenceServer.addUserPresence(userName, contextName, myActor);
    }

    /**
     * Take note that a user has exited one of this client's contexts.
     *
     * @param userName  The name of the user who exited.
     * @param contextName  The name of the context they exited.
     */
    void noteUserExit(String userName, String contextName) {
        myPresenceServer.removeUserPresence(userName, contextName, myActor);
    }

    /**
     * Take note that a context's user's presence info is not visible.
     *
     * @param contextName  The name of the context
     */
    void noteInvisibleContext(String contextName) {
        myPresenceServer.removeVisibleContext(contextName);
    }

    /**
     * Take note that a context's user's presence info is visible.
     *
     * @param contextName  The name of the context
     */
    void noteVisibleContext(String contextName) {
        myPresenceServer.addVisibleContext(contextName, myActor);
    }

    /**
     * Take note that a context is interested in presence updates for some
     * domain.
     *
     * @param contextName  The name of the context that is interested
     * @param domain   The domain of interest
     */
    void subscribeToUpdates(String contextName, String domain) {
        myPresenceServer.addSubscriber(contextName, domain, myActor);
    }

    /**
     * Take note that a context is no longer interested in presence updates.
     *
     * @param contextName  The name of the context that has lost interest
     */
    void unsubscribeToUpdates(String contextName) {
        myPresenceServer.removeSubscriber(contextName, myActor);
    }
}
