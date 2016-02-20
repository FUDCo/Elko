package org.elkoserver.server.director;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.json.StaticTypeResolver;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.HashMapMulti;
import org.elkoserver.util.HashSetMulti;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Director.
 */
class Director {
    /** Table for mapping object references in messages. */
    private RefTable myRefTable;

    /** The server object. */
    private Server myServer;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Flag that is set once server shutdown begins. */
    private boolean amShuttingDown;

    /** Open contexts.  Maps context names to OpenContext objects */
    private Map<String, OpenContext> myContexts;

    /** Open context clone groups.  Maps context set names to sets of
        OpenContext objects. */
    private HashMapMulti<String, OpenContext> myContextCloneSets;

    /** Online users.  Maps user names to sets of OpenContext objects. */
    private HashMapMulti<String, OpenContext> myUsers;

    /** Online user context clone groups.  Maps user set names to sets of
        OpenContext objects. */
    private HashMapMulti<String, OpenContext> myUserCloneSets;

    /** Currently active providers (sorted by load). */
    private TreeMap<Provider, Provider> myProviders;

    /** Estimated amount of load increase from sending a user to a context. */
    private double myEstimatedLoadIncrement;

    /** Value for myEstimatedLoadIncrement if not provided by configuration */
    private static final double DEFAULT_ESTIMATED_LOAD_INCREMENT = 0.0008;

    /** Maximum number of providers supported. */
    private int myProviderLimit;

    /** The admin object. */
    private AdminHandler myAdminHandler;

    /** The provider object. */
    private ProviderHandler myProviderHandler;

    /** Map of context names to sets of watching admin actors. */
    private HashMapMulti<String, DirectorActor> myWatchedContexts;

    /** Map of user names to sets of watching admin actors. */
    private HashMapMulti<String, DirectorActor> myWatchedUsers;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Director(Server server, Trace appTrace) {
        myServer = server;
        tr = appTrace;

        myRefTable = new RefTable(StaticTypeResolver.theStaticTypeResolver);

        myProviderHandler = new ProviderHandler(this);
        myRefTable.addRef(myProviderHandler);
        myRefTable.addRef("session", myProviderHandler);

        myRefTable.addRef(new UserHandler(this));

        myAdminHandler = new AdminHandler(this);
        myRefTable.addRef(myAdminHandler);

        myContexts = new HashMap<String, OpenContext>();
        myContextCloneSets = new HashMapMulti<String, OpenContext>();
        myUserCloneSets = new HashMapMulti<String, OpenContext>();
        myUsers = new HashMapMulti<String, OpenContext>();
        myProviders = new TreeMap<Provider, Provider>();
        myProviderLimit =
            server.props().intProperty("conf.director.providerlimit", 0);
        myEstimatedLoadIncrement =
            server.props().doubleProperty("conf.director.estloadbump",
                                          DEFAULT_ESTIMATED_LOAD_INCREMENT);
        myWatchedContexts = new HashMapMulti<String, DirectorActor>();
        myWatchedUsers = new HashMapMulti<String, DirectorActor>();

        amShuttingDown = false;
        server.registerShutdownWatcher(new ShutdownWatcher() {
            public void noteShutdown() {
                amShuttingDown = true;
                LinkedList<Provider> doomedProviders =
                    new LinkedList<Provider>(myProviders.keySet());
                myProviders.clear();
                for (Provider provider : doomedProviders) {
                    provider.actor().close();
                }
            }
        });
    }

    /**
     * Add a new context to the table of known contexts.
     *
     * @param context  Context description.
     */
    void addContext(OpenContext context) {
        String name = context.name();
        myContexts.put(name, context);
        noteWatchedContext(name);
        if (context.isClone()) {
            name = context.cloneSetName();
            myContextCloneSets.add(name, context);
            noteWatchedContext(name);
        }
    }

    /**
     * Add a new provider to the set of known providers.
     *
     * @param provider  The provider to add.
     */
    void addProvider(Provider provider) {
        myProviders.put(provider, provider);
    }

    /**
     * Add a new user to the table of known users.
     *
     * @param userName  The name of the user.
     * @param context  The context the user is in.
     */
    void addUser(String userName, OpenContext context) {
        myUsers.add(userName, context);
        noteWatchedUser(userName);
        if (isUserClone(userName)) {
            userName = userCloneSetName(userName);
            myUserCloneSets.add(userName, context);
            noteWatchedUser(userName);
        }
    }

    /**
     * Return a set of context clones.
     *
     * @param contextName  The name of the clone context group sought
     *
     * @return a multi-set of the contexts that are clones of 'contextName'.
     */
    HashSetMulti<OpenContext> contextClones(String contextName) {
        return myContextCloneSets.getMulti(contextName);
    }

    /**
     * Return a read-only view of the set of known contexts.
     *
     * @return the collection of known contexts.
     */
    Collection<OpenContext> contexts() {
        return Collections.unmodifiableCollection(myContexts.values());
    }

    /**
     * Do the work of relaying a message embedded in another message.
     *
     * @param from  The entity being relayed from.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param msg  The message to relay.
     */
    void doRelay(DirectorActor from, OptString optContext, OptString optUser,
                 JSONObject msg)
        throws MessageHandlerException
    {
        String contextName = optContext.value(null);
        String userName = optUser.value(null);
        JSONLiteral relay =
            msgRelay(myProviderHandler, contextName, userName, msg);
        targetedBroadCast(from.provider(), contextName, userName, relay);
    }

    /**
     * Lookup a context by name.
     *
     * @param contextName  The name of the context sought.
     *
     * @return the context with the given name, or null if there isn't one.
     */
    OpenContext getContext(String contextName) {
        return myContexts.get(contextName);
    }

    /**
     * Test if a particular user is online.
     */
    boolean hasUser(String userName) {
        return
            myUsers.containsKey(userName) ||
            myUserCloneSets.containsKey(userName);
    }

    /**
     * Test if this director already has all the providers it can handle.
     *
     * @return true if the number of providers connected reaches or exceeds
     *    the provider limit.
     */
    boolean isFull() {
        return myProviderLimit > 0 && myProviders.size() >= myProviderLimit;
    }

    /**
     * Test if the server is in the midst of shutdown.
     *
     * @return true if the server is trying to shutdown.
     */
    boolean isShuttingDown() {
        return amShuttingDown;
    }

    /**
     * Test if a user name is the name of a user clone.
     *
     * @param userName  The user name to test.
     *
     * @return true if 'userName' is the name of a clone.  It is assumed to
     *    be a clone name if it contains more than one '-' character.
     */
    static boolean isUserClone(String userName) {
        return userName.indexOf('-') != userName.lastIndexOf('-');
    }

    /**
     * Determine what provider to use for some context service.  Pick the least
     * loaded appropriate provider.
     *
     * @param contextName  The name of the context sought.
     * @param protocol  The protocol desired for speaking to the provider.
     * @param internal  Flag indicating a request from within the server farm
     *
     * @return the appropriate server for the given service and protocol, or
     *    null if there is no appropriate server.
     */
    Provider locateProvider(String contextName, String protocol,
                            boolean internal)
    {
        String service = serviceName(contextName);

        String logString = "";
        for (Provider provider : myProviders.keySet()) {
            logString += "[" + provider + "/" + provider.loadFactor() + "]";
        }

        for (Provider provider : myProviders.keySet()) {
            if (provider.willServe(service, protocol, internal)) {
                provider.setLoadFactor(provider.loadFactor() +
                                       myEstimatedLoadIncrement);
                tr.eventm("choose " + provider + " from " + logString);
                return provider;
            }
        }
        return null;
    }

    /**
     * Check if anybody needs to be notified about a watched context.
     *
     * @param contextName  The name of the context that opened or closed.
     */
    void noteWatchedContext(String contextName) {
        for (DirectorActor admin : myWatchedContexts.getMulti(contextName)) {
            myAdminHandler.findContext(contextName, admin);
        }
    }

    /**
     * Check if anybody needs to be notified about a watched user.
     *
     * @param userName  The name of the user who entered or exited.
     */
    void noteWatchedUser(String userName) {
        for (DirectorActor admin : myWatchedUsers.getMulti(userName)) {
            myAdminHandler.findUser(userName, admin);
        }
    }

    /**
     * Return this director's provider handler object.
     */
    ProviderHandler providerHandler() {
        return myProviderHandler;
    }

    /**
     * Get a read-only view of the set of known providers.
     *
     * @return the set of known providers.
     */
    Set<Provider> providers() {
        return Collections.unmodifiableSet(myProviders.keySet());
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
     * Remove a context from the set of known contexts.
     *
     * @param context  The context to remove.
     */
    void removeContext(OpenContext context) {
        for (String userName : context.users()) {
            removeUser(userName, context);
        }
        String name = context.name();
        myContexts.remove(name);
        noteWatchedContext(name);
        if (context.isClone()) {
            name = context.cloneSetName();
            myContextCloneSets.remove(name, context);
            noteWatchedContext(name);
        }
    }

    /**
     * Remove a provider from the set of known providers.
     *
     * @param provider  The provider to remove.
     */
    void removeProvider(Provider provider) {
        myProviders.remove(provider);
    }

    /**
     * Remove a user from some context in the set of known users.
     *
     * @param userName  The name of the user.
     * @param context  The context the user was in.
     */
    void removeUser(String userName, OpenContext context) {
        myUsers.remove(userName, context);
        noteWatchedUser(userName);
        if (isUserClone(userName)) {
            userName = userCloneSetName(userName);
            myUserCloneSets.remove(userName, context);
            noteWatchedUser(userName);
        }
    }

    /**
     * Extract the service name from a context name.
     *
     * @param context  The context name.
     */
    private String serviceName(String context) {
        int delim = context.indexOf('-');
        if (delim < 0) {
            return context;
        } else {
            return context.substring(0, delim);
        }
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    void shutdownServer(boolean kill) {
        myServer.shutdown(kill);
    }

    /**
     * Send a message to one or more providers based on whether they currently
     * host a particular context and/or user.
     *
     * @param omitProvider  One provider not to be sent to, or null if it
     *     should be sent to all of them.
     * @param contextRef  The name of a context, or null if don't care.
     * @param userRef  The name of a user, or null if don't care.
     * @param msg  The message to send.
     *
     * @throw MessageHandlerException if there was a problem doing this.
     */
    void targetedBroadCast(Provider omitProvider, String contextRef,
                           String userRef, JSONLiteral msg)
        throws MessageHandlerException
    {
        OpenContext context = null;
        HashSetMulti<OpenContext> clones = null;
        if (contextRef != null) {
            context = getContext(contextRef);
            if (context == null) {
                clones = contextClones(contextRef);
                if (clones.isEmpty()) {
                    throw new MessageHandlerException(
                        "context " + contextRef + " not found");
                }
            }
        }

        boolean directorHasUser = false;
        if (userRef != null) {
            directorHasUser = hasUser(userRef);
            if (!directorHasUser) {
                throw new MessageHandlerException(
                    "user " + userRef + " not found");
            }
        }

        if (context != null) {
            if (directorHasUser) {
                if (!context.hasUser(userRef)) {
                    throw new MessageHandlerException("user " + userRef +
                        " is not in context " + contextRef);
                }
            }
            Provider provider = context.provider();
            if (provider != omitProvider) {
                provider.actor().send(msg);
            }
        } else if (clones != null) {
            for (Provider provider : myProviders.keySet()) {
                if (provider != omitProvider && provider.hasClone(contextRef)){
                    if (userRef == null || provider.hasUser(userRef)) {
                        provider.actor().send(msg);
                    }
                }
            }
        } else if (directorHasUser) {
            for (Provider provider : myProviders.keySet()) {
                if (provider != omitProvider && provider.hasUser(userRef)) {
                    provider.actor().send(msg);
                }
            }
        } else {
            throw new MessageHandlerException(
                "request message missing context or user");
        }
    }

    /**
     * Stop watching for the openings and closings of a context.
     *
     * @param contextName  The name of the context not to be watched.
     * @param admin  The administrator who no longer cares.
     */
    void unwatchContext(String contextName, DirectorActor admin) {
        myWatchedContexts.remove(contextName, admin);
    }

    /**
     * Stop watching for the arrivals and departures of a user.
     *
     * @param userName  The name of the user not to be watched.
     * @param admin  The administrator who no longer cares.
     */
    void unwatchUser(String userName, DirectorActor admin) {
        myWatchedUsers.remove(userName, admin);
    }

    /**
     * Lookup a user clone's contexts by name.
     *
     * @param userName  The name of the user context group sought.
     *
     * @return a multi-set of the contexts where clones of the user with the
     *    given name appears.
     */
    HashSetMulti<OpenContext> userCloneContexts(String userName) {
        return myUserCloneSets.getMulti(userName);
    }

    /**
     * Obtain the name of a user clone set from a user name.
     *
     * @param userName  The user name to get the clone set name from.
     *
     * @return the substring of 'userName' up to but not including the
     *    second '-' character.
     */
    static String userCloneSetName(String userName) {
        int dash = userName.indexOf('-');
        dash = userName.indexOf('-', dash + 1);
        return userName.substring(0, dash);
    }

    /**
     * Lookup a user's contexts by name.
     *
     * @param userName  The name of the user sought.
     *
     * @return a multi-set of the contexts where the user with the given name
     *    appears.
     */
    HashSetMulti<OpenContext> userContexts(String userName) {
        return myUsers.getMulti(userName);
    }

    /**
     * Get the names of known users.
     *
     * Return a set of the names of known users.
     */
    Set<String> users() {
        return myUsers.keys();
    }

    /**
     * Watch for the openings and closings of a context.
     *
     * @param contextName  The name of the context to be watched.
     * @param admin  Who wants to know?
     */
    void watchContext(String contextName, DirectorActor admin) {
        myWatchedContexts.add(contextName, admin);
    }

    /**
     * Watch for the arrivals and departures of a user.
     *
     * @param userName  The name of the user to be watched.
     * @param admin  Who wants to know?
     */
    void watchUser(String userName, DirectorActor admin) {
        myWatchedUsers.add(userName, admin);
    }

    /**
     * Generate a 'relay' message.
     */
    static JSONLiteral msgRelay(Referenceable target, String contextName,
                                String userName, JSONObject relay)
    {
        JSONLiteral msg = new JSONLiteral(target, "relay");
        msg.addParameterOpt("context", contextName);
        msg.addParameterOpt("user", userName);
        msg.addParameter("msg", relay);
        msg.finish();
        return msg;
    }
}
