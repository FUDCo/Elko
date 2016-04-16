package org.elkoserver.server.presence;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.StaticTypeResolver;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Presence Server.
 */
class PresenceServer {
    /** Database that this server stores stuff in. */
    private ObjDB myODB;

    /** Table for mapping object references in messages. */
    private RefTable myRefTable;

    /** Server object. */
    private Server myServer;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Flag that is set once server shutdown begins. */
    private boolean amShuttingDown;

    /** Set of currently connected actors. */
    private Set<PresenceActor> myActors;

    /** Currently online users and what we know about them. */
    private Map<String, ActiveUser> myUsers;

    /** Currently subscribing contexts whose users are visible, represented
        as a map from context ref to associated client. */
    private Map<String, PresenceActor> myVisibles;

    /** Known context metadata. */
    private Map<String, JSONObject> myContextMetadata;

    /** The admin object. */
    private AdminHandler myAdminHandler;

    /** The client object. */
    private ClientHandler myClientHandler;

    /** The social graphs theselves. */
    private Map<String, SocialGraph> mySocialGraphs;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    PresenceServer(Server server, Trace appTrace) {
        myServer = server;
        tr = appTrace;

        myRefTable = new RefTable(StaticTypeResolver.theStaticTypeResolver);

        myClientHandler = new ClientHandler(this);
        myRefTable.addRef(myClientHandler);

        myAdminHandler = new AdminHandler(this);
        myRefTable.addRef(myAdminHandler);

        myActors = new HashSet<PresenceActor>();
        myUsers = new HashMap<String, ActiveUser>();
        myVisibles = new HashMap<String, PresenceActor>();
        myContextMetadata = new HashMap<String, JSONObject>();

        myODB = server.openObjectDatabase("conf.presence");
        if (myODB == null) {
            tr.fatalError("no database specified");
        }
        myODB.addClass("graphtable", GraphTable.class);

        mySocialGraphs = new HashMap<String, SocialGraph>();
        myODB.getObject("graphs", null, new ArgRunnable() {
            public void run(Object obj) {
                if (obj != null) {
                    GraphTable info = (GraphTable) obj;
                    for (GraphDesc desc : info.graphs) {
                        SocialGraph graph = desc.init(PresenceServer.this);
                        if (graph != null) {
                            mySocialGraphs.put(graph.domain().name(), graph);
                        }
                    }
                } else {
                    tr.warningi("unable to load social graph metadata table");
                }
            }
        });

        amShuttingDown = false;
        server.registerShutdownWatcher(new ShutdownWatcher() {
            public void noteShutdown() {
                amShuttingDown = true;
                List<PresenceActor> actorListCopy =
                    new LinkedList<PresenceActor>(myActors);
                for (PresenceActor actor : actorListCopy) {
                    actor.doDisconnect();
                }
                myODB.shutdown();
            }
        });
    }

    /**
     * Get a read-only view of the set of connected actors.
     *
     * @return the set of connected actors.
     */
    Set<PresenceActor> actors() {
        return Collections.unmodifiableSet(myActors);
    }

    /**
     * Add a new actor to the table of connected actors.
     *
     * @param actor  The actor to add.
     */
    void addActor(PresenceActor actor) {
        myActors.add(actor);
    }

    void addSubscriber(String context, String domain, PresenceActor client) {
        SocialGraph graph = mySocialGraphs.get(domain);
        if (graph == null) {
            tr.warningi("client " + client +
                        " attempts to subscribe to non-existent domain '" +
                        domain + "' in context " + context);
        } else {
            graph.domain().addSubscriber(context, client);
        }
    }

    void updateDomain(String domain, JSONObject conf, PresenceActor client) {
        SocialGraph graph = mySocialGraphs.get(domain);
        if (graph == null) {
            tr.warningi("client " + client +
                        " attempts to update non-existent domain '" +
                        domain + "'");
        } else {
            graph.update(this, graph.domain(), conf);
        }
    }

    void removeSubscriber(String context, PresenceActor client) {
        for (SocialGraph graph : mySocialGraphs.values()) {
            graph.domain().removeSubscriber(context);
        }
    }

    void addVisibleContext(String context, PresenceActor client) {
        myVisibles.put(context, client);
    }

    void removeVisibleContext(String context) {
        myVisibles.remove(context);
    }

    private ActiveUser getUser(String userRef) {
        ActiveUser user = myUsers.get(userRef);
        if (user == null) {
            user = new ActiveUser(userRef);
            myUsers.put(userRef, user);
            for (SocialGraph graph : mySocialGraphs.values()) {
                graph.loadUserGraph(user);
            }
        }
        return user;
    }

    /**
     * Add a new user to the collection of online user presences.
     *
     * @param userRef  The reference string for the new user.
     * @param context  The name of the context the user is in.
     * @param client  Actor connected to context server user is on.
     */
    void addUserPresence(String userRef, String context, PresenceActor client)
    {
        if (isVisible(context)) {
            ActiveUser user = getUser(userRef);
            user.addPresence(context, this);
        }
    }

    /**
     * Take note of user metadata.
     *
     * @param userRef  The user to whom the metadata applies
     * @param userMeta  The user metadata itself.
     */
    void noteUserMetadata(String userRef, JSONObject userMeta) {
        ActiveUser user = getUser(userRef);
        user.noteMetadata(userMeta);
    }

    /**
     * Take note of context metadata.
     *
     * @param contextRef  The context to which the metadata applies
     * @param contextMeta  The context metadata itself.
     */
    void noteContextMetadata(String contextRef, JSONObject contextMeta) {
        myContextMetadata.put(contextRef, contextMeta);
    }

    /**
     * Obtain whatever metadata this presence server is holding for a given
     * context.
     *
     * @param contextRef  The ref of the context of interest.
     *
     * @return a metadata object for the given context, or null if there is
     *    none.
     */
    JSONObject getContextMetadata(String contextRef) {
        return myContextMetadata.get(contextRef);
    }

    /**
     * Obtain the application trace object for this presence server.
     *
     * @return the prsence server's trace object.
     */
    public Trace appTrace() {
        return tr;
    }

    /**
     * Get the handler for client messages.
     */
    ClientHandler clientHandler() {
        return myClientHandler;
    }

    /**
     * Obtain the active user info for a named user.
     *
     * @param userRef  The reference string for the user of interest
     *
     * @return the active user info for the named user, or null if that user
     *    currently has no presences.
     */
    ActiveUser getActiveUser(String userRef) {
        return myUsers.get(userRef);
    }

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    boolean isShuttingDown() {
        return amShuttingDown;
    }

    public ObjDB objDB() {
        return myODB;
    }

    /**
     * Return the object ref table.
     */
    RefTable refTable() {
        return myRefTable;
    }

    /**
     * Reinitialize the server.
     */
    void reinitServer() {
        myServer.reinit();
    }

    /**
     * Remove an actor from the set of connected actors.
     *
     * @param actor  The actor to remove.
     */
    void removeActor(PresenceActor actor) {
        myActors.remove(actor);
        for (SocialGraph graph : mySocialGraphs.values()) {
            graph.domain().removeClient(actor);
        }
        Iterator<PresenceActor> iter = myVisibles.values().iterator();
        while (iter.hasNext()) {
            PresenceActor client = iter.next();
            if (client == actor) {
                iter.remove();
            }
        }
    }

    boolean isVisible(String context) {
        return myVisibles.containsKey(context);
    }

    /**
     * Remove a departing user from the collection of online user presences.
     *
     * @param userRef  The reference string for the departing user.
     * @param context  The name of the context the user is leaving.
     * @param client  Actor connected to context server user was on.
     */
    void removeUserPresence(String userRef, String context,
                            PresenceActor client)
    {
        if (isVisible(context)) {
            ActiveUser user = myUsers.get(userRef);
            if (user != null) {
                if (!user.removePresence(context, this)) {
                    tr.warningm("requested to remove user " + userRef +
                                " from unexpected presence " + context + "/" +
                                client);
                }
                if (user.presenceCount() == 0) {
                    myUsers.remove(userRef);
                }
            } else {
                tr.warningm("requested to remove unknown user " + userRef +
                            " from presence " + context + "/" + client);
            }
        }
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately without cleaning up.
     */
    void shutdownServer(boolean kill) {
        for (SocialGraph graph : mySocialGraphs.values()) {
            graph.shutdown();
        }
        myServer.shutdown(kill);
    }

    /**
     * Return the current number of active users
     */
    public int userCount() {
        return myUsers.size();
    }

    /**
     * Return an iterable collection of all the active users.
     */
    public Collection<ActiveUser> users() {
        return myUsers.values();
    }
}
