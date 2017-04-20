package org.elkoserver.server.context;

import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Parser;
import org.elkoserver.json.Referenceable;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.HashMapMulti;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Context Server.
 */
public class Contextor extends RefTable {
    /** Database that persistent objects are stored in. */
    private ObjDB myODB;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** The server object. */
    private Server myServer;

    /** The generic 'session' object for talking to this server. */
    private Session mySession;

    /** Sets of entities awaiting objects from the object database, by object
        reference string. */
    private Map<String, Set<ArgRunnable>> myPendingGets;

    /** Open contexts. */
    private Set<Context> myContexts;

    /** Cloned contexts, by base reference string. */
    private HashMapMulti<String, Context> myContextClones;

    /** Currently connected users. */
    private Set<User> myUsers;

    /** Send group for currently connected directors. */
    private DirectorGroup myDirectorGroup;

    /** Send group for currently connected presence servers. */
    private PresencerGroup myPresencerGroup;

    /** Time user has to enter before being kicked off, in milliseconds. */
    private int myEntryTimeout;

    /** Default enter timeout value, in seconds. */
    private static final int DEFAULT_ENTER_TIMEOUT = 15;

    /** Maximum number of users allowed on this server. */
    private int myLimit;

    /** Static objects loaded from the ODB and available in all contexts. */
    private Map<String, Object> myStaticObjects;

    /** Context families served by this server.  Names prefixed by '$'
        represent restricted contexts. */
    private Set<String> myContextFamilies;

    /** User names gathered from presence notification metadata. */
    private Map<String, String> myUserNames;

    /** Context names gathered from presence notification metadata. */
    private Map<String, String> myContextNames;

    /** Random number generator, for creating unique IDs and sub-IDs. */
    static private SecureRandom theRandom = new SecureRandom();

    /** Mods on completed objects awaiting notification that they're ready. */
    private List<ObjectCompletionWatcher> myPendingObjectCompletionWatchers;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Contextor(Server server, Trace appTrace) {
        this(server.openObjectDatabase("conf.context"), server, appTrace);
    }

    /**
     * Internal constructor.
     *
     * This constructor exists only because of a Java limitation: it needs to
     * create the object database object and then both save it in an instance
     * variable AND pass it to the superclass constructor.  However, Java
     * requires that the first statement in a constructor MUST be a call to
     * the superclass constructor or to another constructor of the same class.
     * It is possible to create the database and pass it to the superclass
     * constructor or to save it in an instance variable, but not both.  To get
     * around this, the public constructor creates the database object in a
     * parameter expression in a call to this internal constructor, which will
     * then possess it in a parameter variable whence it can be both passed to
     * the superclass constructor and saved in an instance variable.
     *
     * @param odb  Database for persistent object storage.
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    private Contextor(ObjDB odb, Server server, Trace appTrace) {
        super(odb);
        tr = appTrace;

        if (odb == null) {
            tr.fatalError("no database specified");
        }
        myODB = odb;
        myODB.addClass("context", Context.class);
        myODB.addClass("item", Item.class);
        myODB.addClass("user", User.class);
        myODB.addClass("serverdesc", ServerDesc.class);
        myODB.addClass("geopos", GeoPosition.class);
        myODB.addClass("cartpos", CartesianPosition.class);

        mySession = new Session(this, server);
        addRef(mySession);

        myServer = server;
        server.setServiceRefTable(this);

        myEntryTimeout = 1000 *
            server.props().intProperty("conf.context.entrytimeout",
                                       DEFAULT_ENTER_TIMEOUT);
        myLimit = server.props().intProperty("conf.context.userlimit", 0);

        myContexts = new HashSet<Context>();
        myContextClones = new HashMapMulti<String, Context>();
        myUsers = new HashSet<User>();
        myDirectorGroup = null;
        myPresencerGroup = null;
        myUserNames = new HashMap<String, String>();
        myContextNames = new HashMap<String, String>();
        myPendingObjectCompletionWatchers = null;
        initializeContextFamilies();
        myPendingGets = new HashMap<String, Set<ArgRunnable>>();
        myStaticObjects = new HashMap<String, Object>();
        loadStaticObjects(server.props().getProperty("conf.context.statics"));

        server.registerShutdownWatcher(new ShutdownWatcher() {
                public void noteShutdown() {
                    /* List copy to avert ConcurrentModificationException */
                    List<User> saveUsers = new LinkedList<User>(myUsers);
                    for (User user : saveUsers) {
                        user.exitContext("server shutting down", "shutdown",
                                         false);
                    }
                    if (myDirectorGroup != null) {
                        myDirectorGroup.disconnectHosts();
                    }
                    if (myPresencerGroup != null) {
                        myPresencerGroup.disconnectHosts();
                    }
                    checkpointAll();
                    myODB.shutdown();
                }
            });
    }

    private void initializeContextFamilies() {
        myContextFamilies = new HashSet<String>();
        myContextFamilies.add("c");
        myContextFamilies.add("ctx");
        myContextFamilies.add("context");
        myContextFamilies.add("$rc");
        myContextFamilies.add("$rctx");
        myContextFamilies.add("$rcontext");
        String families =
            myServer.props().getProperty("conf.context.contexts");
        if (families != null) {
            StringTokenizer tags = new StringTokenizer(families, " ,;:");
            while (tags.hasMoreTokens()) {
                myContextFamilies.add(tags.nextToken());
            }
        }
    }

    private boolean isValidContextRef(String ref) {
        int delim = ref.indexOf('-');
        if (delim < 0) {
            return false;
        } else {
            String family = ref.substring(0, delim);
            return myContextFamilies.contains(family) ||
                myContextFamilies.contains("$" + family);
        }
    }

    /**
     * Add to the list of Mods awaiting notification that their objects are
     * done.
     *
     * @param watcher  The watching Mod to be notified.
     */
    void addPendingObjectCompletionWatcher(ObjectCompletionWatcher watcher) {
        if (myPendingObjectCompletionWatchers == null) {
            myPendingObjectCompletionWatchers = new LinkedList<>();
        }
        myPendingObjectCompletionWatchers.add(watcher);
    }

    /**
     * Notify any Mods still awaiting notification that their objects are done.
     *
     * As a side effect, this will clear the list of who is waiting.
     */
    void notifyPendingObjectCompletionWatchers() {
        if (myPendingObjectCompletionWatchers != null) {
            List<ObjectCompletionWatcher> targets =
                myPendingObjectCompletionWatchers;
            myPendingObjectCompletionWatchers = null;
            for (ObjectCompletionWatcher target : targets) {
                target.objectIsComplete();
            }
        }
    }

    /**
     * Activate an item that is contained by another object as part of its
     * being loaded.
     *
     * @param container  The container into which the item is placed.
     * @param subID  Sub-ID string for cloned objects.  This should be an empty
     *    string if clones are not being generated.
     * @param Item  Inactive item that is being activated.
     */
    void activateContentsItem(BasicObject container, String subID, Item item) {
        String ref = item.ref() + subID;
        item.activate(ref, subID, container.isEphemeral(), this);
        item.setContainerPrim(container);
        item.objectIsComplete();
    }

    /**
     * Take note that somebody is waiting for an object from the object
     * database.
     *
     * @param ref  Reference string for the object being fetched.
     * @param handler  Handler to be invoked on result object.
     *
     * @return true if this is the first pending get for the requested object.
     */
    private boolean addPendingGet(String ref, ArgRunnable handler) {
        Set<ArgRunnable> handlerSet = myPendingGets.get(ref);
        boolean isFirst = false;
        if (handlerSet == null) {
            handlerSet = new HashSet<ArgRunnable>();
            myPendingGets.put(ref, handlerSet);
            isFirst = true;
        }
        handlerSet.add(handler);
        return isFirst;
    }

    /**
     * Add an object to the static object table.
     *
     * @param key  Name by which this object will be known within the server
     * @param obj  The object itself
     */
    void addStaticObject(String key, Object obj) {
        myStaticObjects.put(key, obj);
        if (obj instanceof InternalObject) {
            InternalObject internal = (InternalObject) obj;
            internal.activate(key, this);
            if (internal instanceof AdminObject) {
                addRef(internal);
            }
        }
    }

    /**
     * Obtain the application trace object for this context server.
     *
     * @return the context server's trace object.
     */
    public Trace appTrace() {
        return tr;
    }

    /**
     * Save all changed objects that need saving.
     */
    void checkpointAll() {
        for (DispatchTarget candidate : this) {
            if (candidate instanceof BasicObject) {
                BasicObject object = (BasicObject) candidate;
                object.checkpointWithoutContents();
            }
        }
    }

    /**
     * Get a read-only view of the collection of context families.
     *
     * @return the current set of context families.
     */
    Set<String> contextFamilies() {
        return Collections.unmodifiableSet(myContextFamilies);
    }

    /**
     * Get a read-only view of the context set.
     *
     * @return the current set of open contexts.
     */
    Set<Context> contexts() {
        return Collections.unmodifiableSet(myContexts);
    }

    /**
     * Common initialization logic for createItem and createGeoItem.
     */
    private void initializeItem(Item item, BasicObject container) {
        item.activate(uniqueID("i"), "", false, this);
        item.markAsChanged();
        item.setContainer(container);
    }
    
    /**
     * Return a newly minted Item (i.e., one created at runtime rather than
     * loaded from the object database).  The new item will have no contents,
     * no mods, and no position.  If it is a container, it will be open.
     *
     * @param name  The name for the new item, or null if the name doesn't
     *    matter.
     * @param container  The object that is to be the new item's container.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     *    be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     *    users.
     */
    Item createItem(String name, BasicObject container,
                    boolean isPossibleContainer, boolean isDeletable)
    {
        Item item =
            new Item(name, isPossibleContainer, isDeletable, false, null);
        initializeItem(item, container);
        return item;
    }
    
    /**
     * Return a newly minted geo-positioned Item (i.e., one created at runtime
     * rather than loaded from the object database).  The new item will have
     * neither any contents nor any mods.
     *
     * @param name  The name for the new item, or null if the name doesn't
     *    matter.
     * @param container  The object that is to be the new item's container.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     *    be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     *    users.
     * @param lat  Position latitude, in decimal degrees.
     * @param lon  Position longitude, in decimal degrees.
     */
    Item createGeoItem(String name, BasicObject container,
                       boolean isPossibleContainer, boolean isDeletable,
                       double lat, double lon)
    {
        Item item = new Item(name, isPossibleContainer, isDeletable, false,
                             new GeoPosition(lat, lon));
        initializeItem(item, container);
        return item;
    }
    
    /**
     * Return a newly minted Item (i.e., one created at runtime rather than
     * loaded from the object database).  The new item will be born with no
     * contents, no mods, and no container.
     *
     * @param name  The name for the new item, or null if the name doesn't
     *    matter.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     *    be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     *    users.
     */
    public Item createItem(String name, boolean isPossibleContainer,
                           boolean isDeletable)
    {
        return createItem(name, null, isPossibleContainer, isDeletable);
    }

    /**
     * Return a newly minted geo-positioned Item (i.e., one created at runtime
     * rather than loaded from the object database).  The new item will be born
     * with no contents, no mods, and no container.
     *
     * @param name  The name for the new item, or null if the name doesn't
     *    matter.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     *    be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     *    users.
     * @param lat  Position latitude, in decimal degrees.
     * @param lon  Position longitude, in decimal degrees.
     */
    public Item createGeoItem(String name, boolean isPossibleContainer,
                              boolean isDeletable, double lat, double lon)
    {
        return createGeoItem(name, null, isPossibleContainer, isDeletable,
                             lat, lon);
    }


    /**
     * Create a new (offline) object and store its description in the object
     * database.
     *
     * @param ref  Reference string for the new object, or null to have one
     *    generated automatically.
     * @param contRef  Reference string for the new object's container, or null
     *    to not have it put into a container.
     * @param obj  The new object.
     */
    public void createObjectRecord(String ref, String contRef, BasicObject obj)
    {
        if (ref == null) {
            ref = uniqueID(obj.type());
        }
        myODB.putObject(ref, obj, null, false, null);
    }
    
    /**
     * Delete a user record from the object database.
     *
     * @param ref  Reference string identifying the user to be deleted.
     */
    void deleteUserRecord(String ref) {
        myODB.removeObject(ref, null, null);
    }
    
    /**
     * Deliver a relayed message to an instance of an object.
     *
     * @param destination  Object instance to deliver to.
     * @param message  The message to deliver.
     */
    void deliverMessage(BasicObject destination, JSONObject message) {
        try {
            dispatchMessage(null, destination, message);
        } catch (MessageHandlerException e) {
            tr.eventm("ignoring error from internal msg relay: " + e);
        }
    }

    /**
     * Return the entry timeout value.
     *
     * @return the entry timeout interval, in milliseconds.
     */
    int entryTimeout() {
        return myEntryTimeout;
    }

    /**
     * Extract the base ID from an object reference that might refer to a
     * clone.
     *
     * @param ref  The reference to extract from.
     *
     * @return the base reference string embedded in 'ref', assuming it is a
     *    clone reference (if it is not a clone reference, 'ref' itself will be
     *    returned).
     */
    static String extractBaseRef(String ref) {
        int dash = ref.indexOf('-');
        dash = ref.indexOf('-', dash + 1);
        if (dash < 0) {
            return ref;
        } else {
            return ref.substring(0, dash);
        }
    }
        
    /**
     * Locate an available clone of some context.
     *
     * @param ref  Base reference of the context sought.
     *
     * @return a reference to a clone of the context named by 'ref' that has
     *    room for a new user, or null if 'ref' does not refer to a cloneable
     *    context or if all the clones are full.
     */
    Context findContextClone(String ref) {
        for (Context context : myContextClones.getMulti(ref)) {
            if (context.userCount() < context.baseCapacity() &&
                    !context.gateIsClosed()) {
                return context;
            }
        }
        return null;
    }

    /**
     * Find or make a connection to an external service.
     *
     * @param serviceName  Name of the service being sought.
     * @param handler  A runnable that will be invoked with the relevant
     *    service link once the connection is located or created.  The handler
     *    will be passed a null if no connection was possible.
     */
    public void findServiceLink(String serviceName, ArgRunnable handler) {
        myServer.findServiceLink("workshop-service-" + serviceName, handler);
    }

    /**
     * Obtain a context, either by obtaining a pointer to an already loaded
     * context or, if needed, by loading it.
     *
     * @param contextRef  Reference string identifying the context sought.
     * @param contextTemplate  Optional reference the template context from
     *    which the context should be derived.
     * @param contextHandler  Handler to invoke with resulting context.
     * @param opener  Director that requested this context be opened, or null
     *    if not relevant.
     *
     * @return a Context object that is the requested context, or null if it
     *    could not be obtained.
     */
    void getOrLoadContext(String contextRef, String contextTemplate,
                          ArgRunnable contextHandler, DirectorActor opener)
    {
        if (isValidContextRef(contextRef)) {
            Context result = findContextClone(contextRef);
            if (result == null) {
                result = (Context) get(contextRef);
            }
            if (result == null) {
                if (contextTemplate == null) {
                    contextTemplate = contextRef;
                }
                ArgRunnable getHandler = 
                    new GetContextHandler(contextTemplate, contextRef, opener);
                final ContentsHandler contentsHandler =
                    new ContentsHandler(null, getHandler);
                ArgRunnable contextReceiver = new ArgRunnable() {
                    public void run(Object obj) {
                        contentsHandler.receiveContainer((BasicObject) obj);
                    }
                };
                if (addPendingGet(contextTemplate, contextHandler)) {
                    myODB.getObject(contextTemplate, null, contextReceiver);
                    loadContentsOfContainer(contextRef, contentsHandler);
                }
            } else {
                contextHandler.run(result);
            }
        } else {
            contextHandler.run(null);
        }
    }

    /**
     * Thunk class to receive the contents of a container object.  When a
     * top-level container (i.e., a context or a user) is loaded, we need to
     * also load the container's contents, and the contents of the contents,
     * and so on.  We don't want to signal the top-level container as being
     * successfully loaded until all the things that are descended from it are
     * also loaded.  This class manages that process.  Each instance of this
     * class represents a container that is being loaded; it tracks the loading
     * of its contents and then notifies the container that contains *it*.
     */
    private class ContentsHandler implements ArgRunnable {
        /** Contents handler for the enclosing container, or null if this is
            the top level. */
        private ContentsHandler myParentHandler;

        /** Number of objects whose loading is being awaited.  Initially, this
            is the number of contained objects plus two: the container itself,
            the array of contents objects, and the contents of each of those
            contents objects. It counts down as loading completes. */
        private int myWaitCount;

        /** The container object this handler is handling the loading of
            contents for.  Initially, this is null; it acquires a value when
            the external entity that actually fetches the container object
            calls the receiveContainer() method. */
        private BasicObject myContainer;

        /** Flag indicating that 'myContainer' has been set. */
        private boolean haveContainer;

        /** Array of contents objects whose contents this handler is overseeing
            the recursive loading of.  Initially, this is null; it acquires a
            value when the external entity that actually fetches the contents
            objects calls the receiveContents() method. */
        private Item[] myContents;

        /** Flag indicating that 'myContents' has been set. */
        private boolean haveContents;

        /** Runnable that will be invoked with the root of the entire tree of
            contained objects, once all those objects have been successfully
            loaded. */
        private ArgRunnable myTopHandler;

        /**
         * Constructor.
         *
         * @param parentHandler ContentsHandler for the enclosing parent
         *    container, or null if we are the top level container.
         * @param topHandler Thunk to be notified with the complete result once
         *    it is available.
         */
        ContentsHandler(ContentsHandler parentHandler, ArgRunnable topHandler)
        {
            myParentHandler = parentHandler;
            myTopHandler = topHandler;
            myWaitCount = 2;
            myContents = null;
            haveContents = false;
            myContainer = null;
            haveContainer = false;
        }

        /**
         * Indicate that an additional quantity of objects await being loaded.
         *
         * @param count  The number of additional objects to wait for.
         */
        private void expectMore(int count) {
            myWaitCount += count;
        }

        /**
         * Indicate that some number of objects have been successfully loaded.
         * 
         * @param count The number of objects that have been loaded (typically
         *    this will be 1) or -1 to indicate that all objects that are ever
         *    going to be loaded have been (typically, because an error of some
         *    kind has terminated loading).
         */
        private void somethingArrived(int count) {
            if (myWaitCount >= 0) {
                if (count < 0) {
                    myWaitCount = 0;
                } else {
                    myWaitCount -= count;
                }
                if (myWaitCount == 0) {
                    if (haveContents && haveContainer) {
                        if (myContents != null && myContainer != null) {
                            myContainer.addPassiveContents(myContents);
                        }
                    }
                    myWaitCount = -1;
                    if (myParentHandler == null) {
                        myTopHandler.run(myContainer);
                    } else {
                        myParentHandler.somethingArrived(1);
                    }
                }
            }
        }

        /**
         * Note the arrival of the container object itself.
         *
         * @param container  The container object.
         */
        void receiveContainer(BasicObject container) {
            myContainer = container;
            haveContainer = true;
            somethingArrived(1);
        }

        /**
         * Note the arrival of the contents objects themselves.
         *
         * @param contents Array of contents objects (but not *their*
         *    contents).
         */
        void receiveContents(Item[] contents) {
            myContents = contents;
            haveContents = true;
            somethingArrived(1);
        }

        /**
         * Runnable invoked by the ODB to accept the delivery of stuff fetched
         * from the database.
         *
         * @param obj The thing that was obtained from the database.  In the
         *    current case, this will *always* be an array of objects
         *    representing the contents of the container object this handler is
         *    handling.
         */
        public void run(Object obj) {
            Item[] contents = null;
            if (obj != null) {
                Object[] rawContents = (Object[]) obj;
                if (rawContents.length == 0) {
                    somethingArrived(-1);
                } else {
                    expectMore(rawContents.length);
                    contents = new Item[rawContents.length];
                    for (int i = 0; i < rawContents.length; ++i) {
                        Item item = (Item) rawContents[i];
                        contents[i] = item;
                        if (item.isContainer() && !item.isClosed()) {
                            ContentsHandler subHandler =
                                new ContentsHandler(this, myTopHandler);
                            subHandler.receiveContainer(item);
                            loadContentsOfContainer(item.ref(), subHandler);
                        } else {
                            somethingArrived(1);
                        }
                    }
                    receiveContents(contents);
                }
            } else {
                somethingArrived(-1);
            }
        }
    }

    /**
     * Fetch the (direct) contents of a container from the repository.
     *
     * @param containerRef  Ref of the container object.
     * @param handler  Runnable to be invoked with the retrieved objects.
     */
    private void loadContentsOfContainer(String containerRef,
                                         ArgRunnable handler)
    {
        queryObjects(contentsQuery(extractBaseRef(containerRef)), null, 0,
                     handler);
    }

    /**
     * Generate and return a MongoDB query to fetch an object's contents.
     *
     * @param ref  The ref of the container whose contents are of interest.
     *
     * @return a JSON object representing the above described query.
     */
    static JSONObject contentsQuery(String ref) {
        // { type: "item", in: REF }
        JSONObject query = new JSONObject();
        query.addProperty("type", "item");
        query.addProperty("in", ref);
        return query;
    }

    /**
     * Thunk class to receive a context object fetched from the database.  At
     * the point this is invoked, the context and all of its contents are
     * loaded but not activated.
     */
    private class GetContextHandler implements ArgRunnable {
        /** The ref of the context template.  This is the ref of the object
            that is loaded from the database.*/
        private String myContextTemplate;

        /** The ref of the context itself.  This is the base ref of the context
            that actually results.  It will be the same as the template ref if
            the context is not actually templated. */
        private String myContextRef;

        /** The director that requested this context to be activated. */
        private DirectorActor myOpener;

        /**
         * Constructor
         *
         * @param contextTemplate  The ref of the context template.
         * @param contextRef  The ref of the context itself.
         * @param opener  The director who requested the context activation.
         */
        GetContextHandler(String contextTemplate, String contextRef,
                          DirectorActor opener)
        {
            myContextTemplate = contextTemplate;
            myContextRef = contextRef;
            myOpener = opener;
        }

        /**
         * Callback that will be invoked when the context is loaded.
         *
         * @param obj  The object that was fetched.  This will be a Context
         *    object with a fully expanded (but unactivated) contents tree.
         */
        public void run(Object obj) {
            Context context = null;
            if (obj instanceof Context) {
                context = (Context) obj;
            }
            if (context != null) {
                boolean spawningTemplate =
                    !myContextRef.equals(myContextTemplate);
                boolean spawningClone = context.baseCapacity() > 0;
                if (!spawningTemplate && context.isMandatoryTemplate()) {
                    tr.errorm("context '" + myContextRef +
                              "' may only be used as a template");
                    context = null;
                } else if (!spawningTemplate ||
                           context.isAllowableTemplate()) {
                    String subID = "";
                    if (spawningClone || spawningTemplate) {
                        subID = uniqueID("");
                    }
                    if (spawningClone) {
                        myContextRef += subID;
                    }
                    context.activate(myContextRef, subID,
                                     !myContextRef.equals(myContextTemplate),
                                     Contextor.this, myContextTemplate,
                                     myOpener, tr);
                    context.objectIsComplete();
                    notifyPendingObjectCompletionWatchers();
                } else {
                    tr.errorm("context '" + myContextTemplate +
                              "' may not be used as a template");
                    context = null;
                }
            }
            if (context == null) {
                tr.errorm("unable to load context '" + myContextTemplate +
                          "' as '" + myContextRef + "'");
                resolvePendingGet(myContextTemplate, null);
            } else if (context.isReady()) {
                resolvePendingGet(myContextTemplate, context);
            }
        }
    }

    void resolvePendingInit(BasicObject obj) {
        if (obj.isReady()) {
            resolvePendingGet(obj.baseRef(), obj);
        }
    }

    /**
     * Obtain an item, either by obtaining a pointer to an already loaded item
     * or, if needed, by loading it.
     *
     * @param itemRef  Reference string identifying the item sought.
     * @param itemHandler  Handler to invoke with the resulting item.
     *
     * @return a Item object that is the requested item, or null if it could
     *    not be obtained.
     */
    void getOrLoadItem(String itemRef, ArgRunnable itemHandler) {
        if (itemRef.startsWith("item-") || itemRef.startsWith("i-")) {
            Item result = (Item) get(itemRef);
            if (result == null) {
                if (addPendingGet(itemRef, itemHandler)) {
                    myODB.getObject(itemRef, null,
                                    new GetItemHandler(itemRef));
                }
            } else {
                itemHandler.run(result);
            }
        } else {
            itemHandler.run(null);
        }
    }

    private class GetItemHandler implements ArgRunnable {
        private String myItemRef;

        GetItemHandler(String itemRef) {
            myItemRef = itemRef;
        }

        public void run(Object obj) {
            Item item = null;
            if (obj != null) {
                item = (Item) obj;
                item.activate(myItemRef, "", false, Contextor.this);
                item.objectIsComplete();
            }
            if (item.isReady()) {
                resolvePendingGet(myItemRef, item);
            }
        }
    }

    /**
     * Lookup an object in the static object table.
     *
     * @param ref  Reference string denoting the object of interest.
     *
     * @return the object named 'ref' from the static object table, or null if
     * there is no such object.
     */
    public Object getStaticObject(String ref) {
        return myStaticObjects.get(ref);
    }

    /**
     * Return the limit on how many users are allowed to connect to this
     * server.
     */
    int limit() {
        return myLimit;
    }

    /**
     * Load the contents of a previously closed container.
     *
     * @param item  The item whose contents are to be loaded.
     * @param handler  Handler to be notified once the contents are laoded.
     */
    void loadItemContents(Item item, ArgRunnable handler) {
        ContentsHandler contentsHandler = new ContentsHandler(null, handler);
        contentsHandler.receiveContainer(item);
        loadContentsOfContainer(item.ref(), contentsHandler);
    }

    /**
     * Load the static objects indicated by one or more static object list
     * objects.
     *
     * @param staticListRefs  A comma separated list of statis object list
     *    object names.
     */
    private void loadStaticObjects(String staticListRefs) {
        myODB.addClass("statics", StaticObjectList.class);
        myODB.getObject("statics", null,
                        new StaticObjectListReceiver("statics"));
        if (staticListRefs != null) {
            StringTokenizer tags = new StringTokenizer(staticListRefs, " ,;:");
            while (tags.hasMoreTokens()) {
                String tag = tags.nextToken();
                myODB.getObject(tag, null, new StaticObjectListReceiver(tag));
            }
        }
    }

    private class StaticObjectListReceiver implements ArgRunnable {
        String myTag;
        StaticObjectListReceiver(String tag) {
            myTag = tag;
        }
        public void run(Object obj) {
            StaticObjectList statics = (StaticObjectList) obj;
            if (statics != null) {
                tr.eventi("loading static object list '" + myTag + "'");
                statics.fetchFromODB(myODB, Contextor.this, tr);
            } else {
                tr.errori("unable to load static object list '" + myTag + "'");
            }
        }
    }

    /**
     * Lookup a User object in the object database.
     *
     * @param userRef  Reference string identifying the user sought.
     * @param scope  Application scope for filtering mods
     * @param userHandler  Handler to invoke with the resulting user object or
     *    with null if the user object could not be obtained.
     */
    void loadUser(final String userRef, String scope,
                  ArgRunnable userHandler)
    {
        if (userRef.startsWith("user-") || userRef.startsWith("u-")) {
            if (scope != null) {
                userHandler = new ScopedModAttacher(scope, userHandler);
            }

            ArgRunnable getHandler = new ArgRunnable() {
                public void run(Object obj) {
                    resolvePendingGet(userRef, obj);
                }
            };
            final ContentsHandler contentsHandler =
                new ContentsHandler(null, getHandler);
            ArgRunnable userReceiver = new ArgRunnable() {
                public void run(Object obj) {
                    contentsHandler.receiveContainer((BasicObject) obj);
                }
            };
            if (addPendingGet(userRef, userHandler)) {
                myODB.getObject(userRef, null, userReceiver);
                loadContentsOfContainer(userRef, contentsHandler);
            }
        } else {
            userHandler.run(null);
        }
    }

    /**
     * Generate and return a MongoDB query to fetch an object's non-embedded,
     * application-scoped mods.  These mods are stored in the ODB as
     * independent objects.  Such a mod is identified by a "refx" property and
     * a "scope" property.  The "refx" property corresponds to the ref of the
     * object the mod should be attached to.  The "scope" property matches if
     * its value is a path prefix match for the query's scope parameter.  For
     * example,
     *
     *    scopeQuery("foo", "com-example-thing")
     *
     * would translate to the query pattern:
     *
     * { refx: "foo",
     *   $or: [
     *     { scope: "com" },
     *     { scope: "com-example" },
     *     { scope: "com-example-thing" }
     *   ]
     * }
     *
     * Note that in the future we may decide (based on what's actually the most
     * efficient in the underlying database) to replace the "$or" in the query
     * with a regex property match or some other way of fetching based on a
     * path prefix, so don't take the above expansion as the literal final
     * word.
     *
     * @param ref  The ref of the object in question.
     * @param scope  The application scope
     *
     * @return a JSON object representing the above described query.
     */
    static JSONObject scopeQuery(String ref, String scope) {
        JSONArray orList = new JSONArray();
        String[] frags = scope.split("-");
        String scopePart = null;
        for (String frag : frags) {
            if (scopePart == null) {
                scopePart = frag;
            } else {
                scopePart += '-' + frag;
            }
            JSONObject orTerm = new JSONObject();
            orTerm.addProperty("scope", scopePart);
            orList.add(orTerm);
        }
        JSONObject query = new JSONObject();
        query.addProperty("refx", ref);
        query.addProperty("$or", orList);
        return query;
    }

    /**
     * Thunk to intercept the return of a basic object from the database,
     * generate a query to fetch that object's application-scoped mods, and
     * attach those mods to the object before passing the object to whoever
     * actually asked for it originally.
     */
    private class ScopedModAttacher implements ArgRunnable {
        private String myScope;
        private ArgRunnable myOuterHandler;
        private BasicObject myObj;

        /**
         * Constructor.
         *
         * @param scope  The application scope to be queried against.
         * @param outerHandler  The original handler to which the modified
         *    object should be passed.
         */
        ScopedModAttacher(String scope, ArgRunnable outerHandler) {
            myScope = scope;
            myOuterHandler = outerHandler;
            myObj = null;
        }

        /**
         * Callback that will receive the scoped mod query results.
         *
         * In normal operation, this will end up getting invoked twice.
         *
         * The first time this is called, the arg will be a BasicObject.  This
         * will be the object that was originally requested.  If it is null,
         * then the original query failed and the null will be passed to the
         * outer handler and our work here is done (albeit in a failure state).
         * Otherwise, the ref of the object combined with the scope passed to
         * our constructor are used to form a query to fetch the object's
         * scoped mods.
         *
         * The second time this is called, the arg will be an array of Mod
         * objects (though it may be null or an empty array, which means that
         * the set of mods is empty).  These mods (if there are any) are
         * attached to the object from the first callback and then the object
         * is passed to the outer handler.
         *
         * @param arg  The object or objects fetched from the database, per
         *    the above description.
         */
        public void run(Object arg) {
            if (myObj == null) {
                if (arg == null) {
                    myOuterHandler.run(null);
                } else {
                    myObj = (BasicObject) arg;
                    queryObjects(scopeQuery(myObj.ref(), myScope), null, 0,
                                 this);
                }
            } else {
                if (arg != null) {
                    Object[] rawMods = (Object[]) arg;
                    for (Object rawMod : rawMods) {
                        myObj.attachMod((Mod) rawMod);
                    }
                }
                myOuterHandler.run(myObj);
            }
        }
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
        return myDirectorGroup.lookupReservation(who, where, authCode);
    }

    /**
     * Do record keeping associated with tracking the set of open contexts:
     * tell the directors that a context has been opened or closed and update
     * the context clone collection.
     *
     * @param context  The context.
     * @param open  true if opened, false if closed.
     */
    void noteContext(Context context, boolean open) {
        if (open) {
            myContexts.add(context);
            if (context.baseCapacity() > 0) {
                myContextClones.add(context.baseRef(), context);
            }
        } else {
            myContexts.remove(context);
            if (context.baseCapacity() > 0) {
                myContextClones.remove(context.baseRef(), context);
            }
        }
        if (myDirectorGroup != null) {
            myDirectorGroup.noteContext(context, open);
        }
        if (myPresencerGroup != null) {
            myPresencerGroup.noteContext(context, open);
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
        if (myDirectorGroup != null) {
            myDirectorGroup.noteContextGate(context, open, reason);
        }
    }

    /**
     * Tell the directors that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    void noteUser(User user, boolean on) {
        if (on) {
            myUsers.add(user);
        } else {
            myUsers.remove(user);
        }
        if (myDirectorGroup != null) {
            myDirectorGroup.noteUser(user, on);
        }
        if (myPresencerGroup != null) {
            myPresencerGroup.noteUser(user, on);
        }
    }

    /**
     * Take notice for someone that a user elsewhere has come or gone.
     *
     * @param contextRef  Ref of context of user who cares
     * @param observerRef  Ref of user who cares
     * @param domain  Presence domain of relationship between observer & who
     * @param whoRef  Ref of user who came or went
     * @param whoMeta  Optional metatdata about user who came or went
     * @param whereRef  Ref of the context entered or exited
     * @param whereMeta  Optional metadata about the context entered or exited
     * @param on  True if they came, false if they left
     */
    void observePresenceChange(String contextRef, String observerRef,
                               String domain, String whoRef,
                               JSONObject whoMeta, String whereRef,
                               JSONObject whereMeta, boolean on)
    {
        if (whoMeta != null) {
            try {
                String name = whoMeta.getString("name");
                myUserNames.put(whoRef, name);
            } catch (JSONDecodingException e) {
            }
        }
        if (whereMeta != null) {
            try {
                String name = whereMeta.getString("name");
                myContextNames.put(whereRef, name);
            } catch (JSONDecodingException e) {
            }
        }
        Context subscriber = (Context) get(contextRef);
        if (subscriber != null) {
            subscriber.observePresenceChange(observerRef, domain, whoRef,
                                             whereRef, on);
        } else {
            tr.warningi("presence change of " + whoRef +
                        (on ? " entering " : " exiting ") + whereRef +
                        " for " + observerRef +
                        " directed to unknown context " + contextRef);
        }
    }

    /**
     * Obtain the name metadata for a context, as most recently reported by the
     * presence server.
     *
     * @param contextRef  The context for which the name metadata is sought.
     *
     * @return the name for the given context, or null if none has ever been
     *    reported.
     */
    public String getMetadataContextName(String contextRef) {
        return myContextNames.get(contextRef);
    }

    /**
     * Obtain the name metadata for a user, as most recently reported by the
     * presence server.
     *
     * @param userRef  The user for whom metadata is sought.
     *
     * @return the name for the given user, or null if none has ever been
     *    reported.
     */
    public String getMetadataUserName(String userRef) {
        return myUserNames.get(userRef);
    }

    /**
     * Return a reference to the attached object store.
     *
     * XXX Is this a POLA violation??
     */
    public ObjDB odb() {
        return myODB;
    }

    /**
     * Push a user to a different context: obtain a reservation for the new
     * context, send it to the user, and then kick them out.  If we're not
     * using a director, just send them directly without a reservation.
     *
     * @param who  The user being pushed
     * @param contextRef  The ref of the context to push them to.
     */
    void pushNewContext(User who, String contextRef) {
        if (myDirectorGroup != null) {
            myDirectorGroup.pushNewContext(who, contextRef);
        } else {
            who.exitWithContextChange(contextRef, null, null);
        }
    }

    /**
     * Query the attached object store.
     *
     * @param template  Query template indicating the object(s) desired.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     *    be an array of the object(s) requested, or null if no objects could
     *    be retrieved.
     *
     * XXX Is this a POLA violation??
     */
    public void queryObjects(JSONObject template, String collectionName,
                             int maxResults, ArgRunnable handler) {
        myODB.queryObjects(template, collectionName, maxResults, handler);
    }

    /**
     * Generate a high-quality random number.
     *
     * @return a random long.
     */
    public long randomLong() {
        return theRandom.nextLong();
    }

    /**
     * Register this server's list of listeners with its list of directors.
     *
     * @param directors  List of HostDesc objects describing directors with
     *    whom to register.
     * @param listeners  List of HostDesc objects describing active
     *    listeners to register with the indicated directors.
     */
    void registerWithDirectors(List<HostDesc> directors,
                               List<HostDesc> listeners)
    {
        DirectorGroup group =
            new DirectorGroup(myServer, this, directors, listeners, tr);
        if (group.isLive()) {
            myDirectorGroup = group;
        }
    }

    /**
     * Register this server with its list of presence servers.
     *
     * @param presencers  List of HostDesc objects describing presence servers
     *    with whom to register.
     */
    void registerWithPresencers(List<HostDesc> presencers)
    {
        PresencerGroup group =
            new PresencerGroup(myServer, this, presencers, tr);
        if (group.isLive()) {
            myPresencerGroup = group;
        }
    }

    /**
     * Reinitialize the server.
     */
    void reinitServer() {
        myServer.reinit();
    }

    /**
     * Relay a message from an object to its clones.
     *
     * @param source  Object that is sending the message.
     * @param message  The message itself.
     */
    void relay(BasicObject source, JSONLiteral message) {
        if (source.isClone()) {
            String baseRef = source.baseRef();
            String contextRef = null;
            String userRef = null;

            if (source instanceof Context) {
                contextRef = baseRef;
            } else if (source instanceof User) {
                userRef = baseRef;
            } else {
                throw new Error("relay from inappropriate object");
            }

            JSONObject msgObject = null;
            for (DispatchTarget target : clones(baseRef)) {
                BasicObject obj = (BasicObject) target;
                if (obj != source) {
                    if (msgObject == null) {
                        /* Generating the text form of the message and then
                           parsing it internally may seem like a ludicrously
                           inefficient way to do this, but it saves a vast
                           amount of complication that would otherwise result
                           if internal message relay had to be treated as a
                           special case.  Note that the expensive operations
                           are conditional inside the loop, so that if there is
                           no local relaying to do, no parsing is done, and it
                           is only ever done once in any case.  If this
                           actually turns out to be a performance issue in
                           practice (unlikely, IMHO), this can be revisited. */
                        try {
                            msgObject =
                                new Parser(message.sendableString()).
                                   parseObjectLiteral();
                        } catch (SyntaxError e) {
                            tr.errorm(
                                "syntax error in internal JSON message: " +
                                e.getMessage());
                            break;
                        }
                    }
                    deliverMessage(obj, msgObject);
                }
            }
            if (myDirectorGroup != null) {
                myDirectorGroup.relay(baseRef, contextRef, userRef, message);
            }
        }
    }

    /**
     * Remove an object and all of its contents (recursively) from the table.
     *
     * @param object  The object to remove.
     */
    void remove(BasicObject object) {
        for (Item item : object.contents()) {
            remove(item);
        }
        super.remove(object);
    }

    /**
     * Inform everybody who has been waiting for an object from the object
     * database that the object is here.
     *
     * @param ref  The reference string for the object that arrived.
     * @param obj  The object itself, or null if it could not be obtained.
     */
    private void resolvePendingGet(String ref, Object obj) {
        ref = extractBaseRef(ref);
        Set<ArgRunnable> handlerSet = myPendingGets.get(ref);
        if (handlerSet != null) {
            myPendingGets.remove(ref);
            for (ArgRunnable handler : handlerSet) {
                handler.run(obj);
            }
        }
    }

    /**
     * Obtain the server object for this Context Server.
     *
     * @return this Context Server's server object.
     */
    public Server server() {
        return myServer;
    }

    /**
     * Get the server name.
     *
     * @return the server's name.
     */
    String serverName() {
        return myServer.serverName();
    }

    /**
     * @return the session object for this server.
     */
    Session session() {
        return mySession;
    }

    /**
     * Convert an array of Items into the contents of a container.  This
     * routine does not fiddle with the changed flags and is for use during
     * object construction only.
     *
     * @param container  The container into which the items are being placed.
     * @param subID  Sub-ID string for cloned objects.  This should be an empty
     *    string if clones are not being generated.
     * @param contents  Array of inactive items to be added to the container.
     */
    void setContents(BasicObject container, String subID, Item contents[]) {
        if (contents != null) {
            for (Item item : contents) {
                if (item != null) {
                    activateContentsItem(container, subID, item);
                }
            }
        }
    }

    /**
     * Cause the server to be shutdown.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    void shutdownServer(boolean kill) {
        myServer.shutdown(kill);
    }

    /**
     * Synthesize a User object by having a factory object (from the static
     * object table) produce it.
     *
     * @param connection  The connection over which the new user presented
     *    themself.
     * @param factoryTag  Tag identifying the factory to use
     * @param param  Arbitrary parameter object, which should be consistent
     *    with the factory indicated by 'factoryTag'
     * @param contextRef  Ref of context the new synthesized user will be
     *    placed into
     * @param contextTemplate  Ref of the context template for the context
     * @param scope  Application scope for filtering mods
     * @param userHandler  Handler to invoke with the resulting user object or
     *    with null if the user object could not be produced.
     */
    void synthesizeUser(Connection connection, String factoryTag,
                        final JSONObject param, final String contextRef,
                        final String contextTemplate, String scope,
                        ArgRunnable userHandler)
    {
        Object rawFactory = getStaticObject(factoryTag);
        if (rawFactory == null) {
            tr.errori("user factory '" + factoryTag + "' not found");
            userHandler.run(null);
        } else if (rawFactory instanceof EphemeralUserFactory) {
            EphemeralUserFactory factory = (EphemeralUserFactory) rawFactory;
            User user = factory.provideUser(this, connection, param,
                                            contextRef, contextTemplate);
            user.markAsEphemeral();
            userHandler.run(user);
        } else if (rawFactory instanceof UserFactory) {
            UserFactory factory = (UserFactory) rawFactory;
            factory.provideUser(this, connection, param, userHandler);
        } else {
            tr.errori("factory tag '" + factoryTag +
                      "' does not designate a user factory object");
            userHandler.run(null);
        }
    }

    /**
     * Generate a unique object ID.
     *
     * @param root
     *
     * @return a reference string for a new object with the given root.
     */
    public String uniqueID(String root) {
        return root + '-' + Math.abs(theRandom.nextLong());
    }

    /**
     * Get the current number of users.
     *
     * @return the number of users currently in all contexts.
     */
    int userCount() {
        return myUsers.size();
    }

    /**
     * Get a read-only view of the user set.
     *
     * @return the current set of open users.
     */
    Set<User> users() {
        return Collections.unmodifiableSet(myUsers);
    }

    /**
     * Record an object deletion in the object database.
     *
     * @param ref  Reference string designating the deleted object.
     */
    void writeObjectDelete(String ref) {
        writeObjectDelete(ref, null);
    }

    /**
     * Record an object deletion in the object database, with completion
     * handler.
     *
     * @param ref  Reference string designating the deleted object.
     * @param handler  Completion handler.
     */
    void writeObjectDelete(String ref, ArgRunnable handler) {
        myODB.removeObject(ref, null, handler);
    }

    /**
     * Write an object's state to the object database.
     *
     * @param ref  Reference string of the object to write.
     * @param state  The object state to be written.
     */
    void writeObjectState(String ref, BasicObject state) {
        writeObjectState(ref, state, null);
    }

    /**
     * Write an object's state to the object database, with completion handler.
     *
     * @param ref  Reference string of the object to write.
     * @param state  The object state to be written.
     * @param handler  Completion handler
     */
    void writeObjectState(String ref, BasicObject state, ArgRunnable handler) {
        myODB.putObject(ref, state, null, false, handler);
    }
}
