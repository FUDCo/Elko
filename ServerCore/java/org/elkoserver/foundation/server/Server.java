package org.elkoserver.foundation.server;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.elkoserver.foundation.actor.JSONHTTPFramer;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.StaticTypeResolver;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.ConnectionCountMonitor;
import org.elkoserver.foundation.net.ConnectionRetrier;
import org.elkoserver.foundation.net.HTTPSessionConnection;
import org.elkoserver.foundation.net.JSONByteIOFramerFactory;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetAddr;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.foundation.run.SlowServiceRunner;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.foundation.server.metadata.ServiceFinder;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.objdb.ObjDBLocal;
import org.elkoserver.objdb.ObjDBRemote;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.HashMapMulti;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceController;

/**
 * The core of an Elko server, holding the run queue, a collection of
 * configuration information extracted from the various Java property settings,
 * as well as access to various important server-intrinsic services (such as
 * port listeners) that are configured by those property settings.
 */
public class Server implements ConnectionCountMonitor, ServiceFinder
{
    /** The properties settings. */
    private BootProperties myProps;

    /** The name of this server (for logging). */
    private String myServerName;

    /** Name of service, to distinguish variants of same service type. */
    private String myServiceName;

    /** Network manager, for setting up network communications. */
    private NetworkManager myNetworkManager;

    /** List of ServiceDesc objects describing services this server offers. */
    private List<ServiceDesc> myServices;

    /** List of host information for this server's configured listeners. */
    private List<HostDesc> myListeners;

    /** Connection to the broker, if there is one. */
    private BrokerActor myBrokerActor;

    /** Host description for connection to broker, if there is one. */
    private HostDesc myBrokerHost;

    /** Message dispatcher for broker connections. */
    private MessageDispatcher myDispatcher;

    /** Table of 'find' requests that have been issued to the broker, for which
        responses are still pending.  Indexed by the service name queried. */
    private HashMapMulti<String, ServiceQuery> myPendingFinds;

    /** Counter to generate tags for 'find' requests to the broker. */
    private static int theNextFindTag = 0;

    /** Number of active connections. */
    private int myConnectionCount;

    /** Objects to be notified when the server is shutting down. */
    private List<ShutdownWatcher> myShutdownWatchers;

    /** Objects to be notified when the server is reinitialized. */
    private List<ReinitWatcher> myReinitWatchers;

    /** Accumulator tracking system load. */
    private ServerLoadMonitor myLoadMonitor;

    /** Trace object for event logging. */
    private Trace tr;

    /** Trace object for mandatory startup and shutdown messages. */
    private Trace trServer;

    /** Run queue that the server services its clients in. */
    private Runner myMainRunner;

    /** Thread pool isolation for external blocking tasks. */
    private SlowServiceRunner mySlowRunner;

    /** Flag that server is in the midst of trying to shut down. */
    private boolean amShuttingDown;

    /** Default value for max number of threads in slow service thread pool. */
    private static final int DEFAULT_SLOW_THREADS = 5;

    /** Map from external service names to links to the services. */
    private Map<String, ServiceLink> myServiceLinksByService;

    /** Map from external service provider IDs to connected actors. */
    private Map<Integer, ServiceActor> myServiceActorsByProviderID;

    /** Active service actors associated with broken broker connections. */
    private List<ServiceActor> myOldServiceActors;

    /* RefTable to dispatching messages incoming from external services. */
    private RefTable myServiceRefTable;

    /**
     * Generate the Server from Java properties.
     *
     * @param props  The properties, as determined by the boot process.
     * @param serverType  Server type tag (for generating property names).
     * @param appTrace  Trace object for event logging.
     */
    public Server(BootProperties props, String serverType, Trace appTrace) {
        myProps = props;
        myConnectionCount = 0;
        myMainRunner = Runner.currentRunner();
        mySlowRunner =
            new SlowServiceRunner(myMainRunner,
                                  props.intProperty("conf.slowthreads",
                                                    DEFAULT_SLOW_THREADS));
        amShuttingDown = false;
        myShutdownWatchers = new LinkedList<ShutdownWatcher>();
        myReinitWatchers = new LinkedList<ReinitWatcher>();
        myListeners = null;
        myServices = new LinkedList<ServiceDesc>();

        tr = appTrace;
        trServer = Trace.trace("server");
        TraceController.setProperty("trace_server", "WORLD");
        TraceController.setProperty("trace_trace", "WORLD");

        myServiceName = props.getProperty("conf." + serverType + ".service");
        if (myServiceName == null) {
            myServiceName = "";
        } else {
            myServiceName = "-" + myServiceName;
        }

        myServerName =
            props.getProperty("conf." + serverType + ".name", "<anonymous>");

        trServer.noticei(version());
        trServer.noticei("Copyright 2011 Chip Morningstar; see LICENSE");
        trServer.noticei("Starting " + myServerName);

        myLoadMonitor = new ServerLoadMonitor(this);
        myNetworkManager =
            new NetworkManager(this, props, myLoadMonitor, myMainRunner);

        myServiceLinksByService = new HashMap<String, ServiceLink>();
        myServiceActorsByProviderID = new HashMap<Integer, ServiceActor>();
        myOldServiceActors = new LinkedList<ServiceActor>();
        myServiceRefTable = null;

        myDispatcher = new MessageDispatcher(
            StaticTypeResolver.theStaticTypeResolver);
        myDispatcher.addClass(BrokerActor.class);
        myPendingFinds = new HashMapMulti<String, ServiceQuery>();
        myBrokerActor = null;
        myBrokerHost = HostDesc.fromProperties(props, "conf.broker");

        if (props.testProperty("conf.msgdiagnostics")) {
            NetworkManager.TheDebugReplyFlag = true;
        }
        if (props.testProperty("conf.debugsessions")) {
            HTTPSessionConnection.TheDebugSessionsFlag = true;
        }
    }

    /**
     * Take note of the connection to the broker.  Send any 'find' requests
     * that were issued prior to the broker connection being made.
     *
     * @param brokerActor  Actor representing the connection to the broker.
     *    If null, this indicates that a connection has been lost and must be
     *    re-established.
     */
    void brokerConnected(BrokerActor brokerActor) {
        myBrokerActor = brokerActor;

        if (brokerActor == null) {
            myOldServiceActors.addAll(myServiceActorsByProviderID.values());
            myServiceActorsByProviderID.clear();
            myServiceLinksByService.clear();
            connectToBroker();
        } else {
            for (String key : myPendingFinds.keys()) {
                for (ServiceQuery query : myPendingFinds.getMulti(key)) {
                    brokerActor.findService(key, query.isMonitor(),
                                            query.tag());
                }
            }
        }
    }

    /**
     * Make a new connection to the broker.
     */
    private void connectToBroker() {
        if (!amShuttingDown) {
            new ConnectionRetrier(myBrokerHost,
                                  "broker",
                                  myNetworkManager,
                                  new BrokerMessageHandlerFactory(), tr);
        }
    }

    private class BrokerMessageHandlerFactory implements MessageHandlerFactory
    {
        public MessageHandler provideMessageHandler(Connection connection) {
            return new BrokerActor(connection, myDispatcher, Server.this,
                                   myBrokerHost);
        }
    }

    /**
     * Track the number of connections, so server can exit gracefully.
     *
     * @param delta  An upward or downward adjustment to the connection count.
     */
    synchronized public void connectionCountChange(int delta) {
        myConnectionCount += delta;
        if (myConnectionCount < 0) {
            tr.errorm("negative connection count: " + myConnectionCount);
        }
        if (amShuttingDown && myConnectionCount <= 0) {
            serverExit();
        }
    }

    /**
     * Drop a runnable onto the main run queue.
     *
     * @param runnable  The thing to run.
     */
    public void enqueue(Runnable runnable) {
        myMainRunner.enqueue(runnable);
    }

    /**
     * Drop a task onto the slow queue.
     *
     * @param task  Callable that executes the task.  This will be executed in
     *    a separate thread and so is permitted to block.
     * @param resultHandler  Thunk that will be invoked with the result
     *    returned by the task.  This will be executed on the main run queue.
     */
    public void enqueueSlowTask(Callable<Object> task,
                                ArgRunnable resultHandler)
    {
        mySlowRunner.enqueueTask(task, resultHandler);
    }

    /**
     * Attempt to reestablish a broken service connection.
     *
     * @param service  Name of the service to reconnect to
     * @param link  Service link to associate with the reestablished connection
     */
    void reestablishServiceConnection(String service, final ServiceLink link){
        findService(service,
                    new ServiceFoundHandler(new ArgRunnable() {
                        public void run(Object obj) {
                            if (obj == null) {
                                link.fail();
                            }
                        }
                    }, service, link), false);
    }

    /**
     * Issue a request for service information to the broker.
     *
     * @param service  The service desired.
     * @param handler  Object to receive the asynchronous result(s).
     * @param monitor  If true, keep watching for more results after the first.
     */
    public void findService(String service, ArgRunnable handler,
                            boolean monitor)
    {
        if (myBrokerHost != null) {
            String tag = "" + (theNextFindTag++);
            myPendingFinds.add(service,
                               new ServiceQuery(service, handler, monitor,
                                                tag));
            if (myBrokerActor != null) {
                myBrokerActor.findService(service, monitor, tag);
            }
        } else {
            tr.errori("can't find service " + service +
                      ", no broker specified");
        }
    }

    /**
     * Obtain a message channel to a service.  Services are located using the
     * broker.  Note that all services offered by a given server are
     * multiplexed through a single connection: if there is an existing
     * connection to the server providing the service, it is used, but if there
     * is no existing connection to that server, one is created.
     *
     * @param service  The service desired
     * @param handler A runnable that will be invoked with a service link to
     *    the requested service once the connection is located or created.  The
     *    handler will be passed a null if no connection was possible.
     */
    public void findServiceLink(String service, ArgRunnable handler) {
        if (!amShuttingDown) {
            ServiceLink link = myServiceLinksByService.get(service);
            if (link != null) {
                handler.run(link);
            } else {
                findService(service,
                            new ServiceFoundHandler(handler, service, null),
                            false);
            }
        }
    }

    /**
     * Handler class to process events associated with establishing a service
     * connection.  Handles (1) processing the result returned by the broke
     * when asked for contact information for a service, and (2) processing the
     * connection to the relevant server once such a connection is first
     * established.
     */
    private class ServiceFoundHandler
        implements ArgRunnable, MessageHandlerFactory
    {
        /** Handler to receive connection to service, once there is one. */
        private ArgRunnable myInnerHandler;

        /** Optional arbitrary label to attach to new connection. */
        private String myLabel;

        /** Service descriptor from the broker, once we have one. */
        private ServiceDesc myDesc;

        /** A service link to be associated with the connection, or null if a
            new link should be allocated. */
        private ServiceLink myLink;

        /**
         * Constructor.
         *
         * @param innerHandler  Handler to receive service connection.
         * @param service  Name of the service being sought.
         * @param link  Service link that will be associated with the
         *    connection; if null, a new link will be created
         */
        public ServiceFoundHandler(ArgRunnable innerHandler, String service,
                                   ServiceLink link)
        {
            myInnerHandler = innerHandler;
            myLabel = service;
            if (link == null) {
                myLink = new ServiceLink(service, Server.this);
            } else {
                myLink = link;
            }
        }

        /**
         * Handle the location of a service by the broker.  If we already have
         * an existing connection to the server providing the service, the
         * service actor associated with that connection is passed to the
         * handler from the original requestor.  Otherwise, we initiate a new
         * connection to the server in question.
         *
         * Note that the broker can return multiple providers for a service,
         * but for the purposes of this API we want exactly one. If more than
         * one provider was located we arbitraily choose the first one but also
         * write a warning to the log.
         *
         * @param obj  Array of service descriptors for the service that was
         *    located.  Normally this will be a single element array.
         */
        public void run(Object obj) {
            ServiceDesc[] descs = (ServiceDesc[]) obj;
            myDesc = descs[0];
            if (myDesc.failure() != null) {
                tr.warningi("service query for " + myLabel + " failed: " +
                            myDesc.failure());
                myInnerHandler.run(null);
                return;
            }
            if (descs.length > 1) {
                tr.warningm("service query for " + myLabel +
                            " returned multiple results; using first one");
            }
            ServiceActor actor =
                myServiceActorsByProviderID.get(myDesc.providerID());
            if (actor != null) {
                connectLinkToActor(actor);
            } else {
                new ConnectionRetrier(myDesc.asHostDesc(-1), myLabel,
                                      myNetworkManager, this, tr);
            }
        }

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        public MessageHandler provideMessageHandler(Connection connection) {
            ServiceActor actor =
                new ServiceActor(connection, myServiceRefTable, myDesc,
                                 Server.this);
            myServiceActorsByProviderID.put(myDesc.providerID(), actor);
            connectLinkToActor(actor);
            return actor;
        }

        /**
         * Common code for wiring a ServiceLink to a ServiceActor and notifying
         * the service requestor, regardless of whether the actor was found or
         * created.
         *
         * @param actor The actor for the new service link.
         */
        private void connectLinkToActor(ServiceActor actor) {
            myServiceLinksByService.put(myDesc.service(), myLink);
            actor.addLink(myLink);
            myInnerHandler.run(myLink);
        }
    }

    /**
     * Handle the broker announcing a service.
     *
     * @param services  Descriptions of the services found.
     * @param tag  Tag string for matching queries with responses.
     */
    void foundService(ServiceDesc services[], String tag) {
        String service = services[0].service(); /* all must have same name */
        Iterator<ServiceQuery> iter =
            myPendingFinds.getMulti(service).iterator();
        while (iter.hasNext()) {
            ServiceQuery query = iter.next();
            if (tag.equals(query.tag())) {
                query.result(services);
                if (!query.isMonitor()) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * Get the configured listeners for this server.
     *
     * @return a read-only list of host information for the currently
     *    configured listeners.
     */
    public List<HostDesc> listeners() {
        return myListeners;
    }

    /**
     * Get this server's network manager.
     *
     * @return the network manager
     */
    public NetworkManager networkManager() {
        return myNetworkManager;
    }

    /**
     * Open an asynchronous object database whose location (directory path or
     * remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the odb
     *    that is to be opened.
     *
     * @return an object for communicating with the opened odb, or null if the
     *    location was not properly specified.
     */
    public ObjDB openObjectDatabase(String propRoot) {
        if (myProps.getProperty(propRoot + ".odb") != null) {
            return new ObjDBLocal(myProps, propRoot, tr);
        } else {
            if (myProps.getProperty(propRoot + ".repository.host") != null ||
                myProps.getProperty(propRoot + ".repository.service") != null)
            {
                return new ObjDBRemote(this, myNetworkManager, myServerName,
                                       myProps, propRoot, tr);
            } else {
                return null;
            }
        }
    }

    /**
     * Get this server's properties.
     *
     * @return the properties
     */
    public BootProperties props() {
        return myProps;
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server samples its load.
     *
     * @param watcher  An object to notify about load samples.
     */
    public void registerLoadWatcher(LoadWatcher watcher) {
        myLoadMonitor.registerLoadWatcher(watcher);
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server samples its load.
     *
     * @param watcher  The object to stop notifying about load samples.
     */
    public void unregisterLoadWatcher(LoadWatcher watcher) {
        myLoadMonitor.unregisterLoadWatcher(watcher);
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server is being reinitialized.
     *
     * @param watcher  An object to notify at reinitialization time.
     */
    public void registerReinitWatcher(ReinitWatcher watcher) {
        myReinitWatchers.add(watcher);
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server is being reinitialized.
     *
     * @param watcher  The object that no longer cares about reinitialization.
     */
    public void unregisterReinitWatcher(ReinitWatcher watcher) {
        myReinitWatchers.remove(watcher);
    }

    /**
     * Add a new service offering to the collection of services provided by
     * this server.
     *
     * @param service  Service descriptor describing the service being added.
     */
    public void registerService(ServiceDesc service) {
        myServices.add(service);
        if (myBrokerActor != null) {
            myBrokerActor.registerService(service);
        }
    }

    /**
     * Add an object to the collection of objects to be notified when the
     * server is being shut down.
     *
     * @param watcher  An object to notify at shutdown time.
     */
    public void registerShutdownWatcher(ShutdownWatcher watcher) {
        myShutdownWatchers.add(watcher);
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server is being shut down.
     *
     * @param watcher  The object that no longer cares about shutdown.
     */
    public void unregisterShutdownWatcher(ShutdownWatcher watcher) {
        myShutdownWatchers.remove(watcher);
    }

    /**
     * Reinitialize the server.
     */
    public void reinit() {
        if (myBrokerActor != null) {
            myBrokerActor.close();
        }
        for (ReinitWatcher watcher : myReinitWatchers) {
            watcher.noteReinit();
        }
    }

    /**
     * Really shut down the server.
     */
    private void serverExit() {
        trServer.worldi("Good bye");
        myMainRunner.orderlyShutdown();
    }        

    /**
     * Get this server's name.
     *
     * @return the server name.
     */
    public String serverName() {
        return myServerName;
    }

    void serviceActorDied(ServiceActor deadActor) {
        myServiceActorsByProviderID.remove(deadActor.providerID());
        for (ServiceLink link : deadActor.serviceLinks()) {
            myServiceLinksByService.remove(link.service());
        }
    }

    /**
     * Get the services being offered by this server.
     *
     * @return a list of ServiceDesc objects describing the services offered by
     *    this server.
     */
    public List<ServiceDesc> services() {
        return Collections.unmodifiableList(myServices);
    }

    /**
     * Assign the ref table that will be used to dispatch messages received
     * from connected services.
     *
     * @param serviceRefTable  The ref table to use.
     */
    public void setServiceRefTable(RefTable serviceRefTable) {
        myServiceRefTable = serviceRefTable;
    }

    /**
     * Shut down the server.  Actually, this initiates the shutdown process;
     * the actual shutdown happens in serverExit().
     *
     * @param kill  If true, shut down immediately instead of cleaning up.
     */
    public void shutdown(boolean kill) {
        if (kill) {
            System.exit(0);
        } else if (!amShuttingDown) {
            amShuttingDown = true;
            trServer.worldi("Shutting down " + myServerName);
            if (myBrokerActor != null) {
                myBrokerActor.close();
            }
            for (ShutdownWatcher watcher : myShutdownWatchers) {
                watcher.noteShutdown();
            }
            for (ServiceActor service : myServiceActorsByProviderID.values()) {
                service.close();
            }
            for (ServiceActor service : myOldServiceActors) {
                service.close();
            }
            if (myConnectionCount <= 0) {
                serverExit();
            }
        }
    }

    /**
     * Start listening for connections on some port.
     *
     * @param propRoot  Prefix string for all the properties describing the
     *    listener that is to be started.
     * @param host  The host:port string for the port to listen on.
     * @param listeners  List upon which to record listeners opened, or null if
     *    the caller is not interested in this information.
     * @param metaFactory   Object that will provide a message handler factory
     *    for connections made to this listener.
     *
     * @return host description for the listener that was started, or null if
     *    the operation failed for some reason.
     */
    private HostDesc startOneListener(String propRoot, String host,
                                      ServiceFactory metaFactory)
    {
        HostDesc result = null;
        String bind = myProps.getProperty(propRoot + ".bind", host);
        boolean noPortConfig = host.indexOf(':') < 0;

        String protocol = myProps.getProperty(propRoot + ".protocol", "tcp");

        AuthDesc auth = AuthDesc.fromProperties(myProps, propRoot, tr);
        if (auth == null) {
            tr.errorm("bad auth info, listener " + propRoot + " not started");
            return null;
        }

        String allowString = myProps.getProperty(propRoot + ".allow");
        Set<String> allow = new HashSet<String>();
        if (allowString != null) {
            StringTokenizer scan = new StringTokenizer(allowString, ",");
            while (scan.hasMoreTokens()) {
                allow.add(scan.nextToken());
            }
        }

        boolean secure = myProps.testProperty(propRoot + ".secure");

        String label = myProps.getProperty(propRoot + ".label");

        List<String> serviceNames = new LinkedList<String>();
        MessageHandlerFactory actorFactory =
            metaFactory.provideFactory(propRoot, auth, allow, serviceNames,
                                       protocol);
        if (actorFactory == null) {
            return null;
        }

        boolean dontLog = myProps.testProperty(propRoot + ".dontlog");
        Trace msgTrace;
        if (dontLog) {
            msgTrace = Trace.none;
        } else if (label != null) {
            msgTrace = Trace.comm.subTrace(label);
        } else {
            msgTrace = Trace.comm.subTrace("cli");
        }

        NetAddr listenAddress = null;

        String mgrClass = myProps.getProperty(propRoot + ".class");
        if (mgrClass != null) {
            try {
                listenAddress =
                    myNetworkManager.listenVia(
                        propRoot,
                        mgrClass,
                        bind,
                        actorFactory,
                        msgTrace,
                        secure);
                result =
                    new HostDesc(protocol, secure, host, auth, -1, dontLog);
                if (noPortConfig) {
                    host += ":" + listenAddress.getPort();
                }
                trServer.noticei("listening for " + protocol +
                    " connections using " + mgrClass + " on " + host +
                    (bind != host ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (secure mode)" : ""));
            } catch (IOException e) {
                tr.errorm("unable to open " + protocol + " (" + mgrClass +
                          ") listener " + propRoot + " on requested host: " +
                          e);
            }
        } else if (protocol.equals("tcp")) {
            try {
                listenAddress =
                    myNetworkManager.listenTCP(
                        bind,
                        actorFactory,
                        new JSONByteIOFramerFactory(msgTrace),
                        secure,
                        msgTrace);
                if (noPortConfig) {
                    host += ":" + listenAddress.getPort();
                }
                result = new HostDesc("tcp", secure, host, auth, -1, dontLog);
                trServer.noticei("listening for tcp connections on " +
                    host + (bind != host ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (using SSL)" : ""));
            } catch (IOException e) {
                tr.errorm("unable to open tcp listener " + propRoot +
                          " on requested host: " + e);
            }
        } else if (protocol.equals("rtcp")) {
            try {
                listenAddress =
                    myNetworkManager.listenRTCP(
                       bind,
                       actorFactory,
                       msgTrace,
                       secure);
                if (noPortConfig) {
                    host += ":" + listenAddress.getPort();
                }
                result = new HostDesc("rtcp", secure, host, auth, -1, dontLog);
                trServer.noticei("listening for rtcp connections on " +
                    host + (bind != host ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (using SSL)" : ""));
            } catch (IOException e) {
                tr.errorm("unable to open rtcp listener " + propRoot +
                          " on requested host: " + e);
            }
        } else if (protocol.equals("http")) {
            try {
                String rootURI = myProps.getProperty(propRoot + ".root", "");
                String pubHost = host;
                String domain = myProps.getProperty(propRoot + ".domain");
                int colon = host.indexOf(':');
                if (colon >= 0) {
                    if (host.substring(colon).equals(secure ? ":443":":80")) {
                        pubHost = host.substring(0, colon);
                    }
                }
                if (domain == null) {
                    if (colon < 0) {
                        domain = host;
                    } else {
                        domain = host.substring(0, colon);
                    }
                    int dot = domain.lastIndexOf('.');
                    dot = domain.lastIndexOf('.', dot - 1);
                    if (dot > 0) {
                        domain = domain.substring(dot + 1);
                    }
                }
                listenAddress =
                    myNetworkManager.listenHTTP(
                       bind,
                       actorFactory,
                       rootURI,
                       new JSONHTTPFramer(msgTrace),
                       secure,
                       msgTrace);

                result = new HostDesc("http", secure, host + "/" + rootURI,
                                       auth, -1, dontLog);
                trServer.noticei("listening for http connections on " +
                    host + "/" + rootURI + "/ in domain " + domain +
                    (bind != host ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (using SSL)" : ""));
            } catch (IOException e) {
                tr.errorm("unable to open http listener " + propRoot +
                          " on requested host: " + e);
            }
        } else if (protocol.equals("ws")) {
            try {
                String socketURI = myProps.getProperty(propRoot + ".sock", "");
                String pubHost = host;
                int colon = host.indexOf(':');
                if (colon >= 0) {
                    if (host.substring(colon).equals(secure ? ":443":":80")) {
                        pubHost = host.substring(0, colon);
                    }
                }
                listenAddress =
                    myNetworkManager.listenWebSocket(
                       bind,
                       actorFactory,
                       socketURI,
                       msgTrace,
                       secure);

                result = new HostDesc("ws", secure, host + "/" + socketURI,
                                      auth, -1, dontLog);
                trServer.noticei("listening for WebSocket connections on " +
                    host + "/" + socketURI +
                    (bind != host ? (" (" + bind + ")") : "") +
                    " (" + auth.mode() + ")" +
                    (secure ? " (using SSL)" : ""));
            } catch (IOException e) {
                tr.errorm("unable to open http listener " + propRoot +
                          " on requested host: " + e);
            }
        } else {
            tr.errorm("unknown value for " + propRoot + ".protocol: " +
                      protocol + ", listener " + propRoot + " not started");
        }
        if (listenAddress != null) {
            for (String serviceName : serviceNames) {
                serviceName += myServiceName;
                registerService(new ServiceDesc(serviceName, host, protocol,
                                                label, auth, null, -1));
            }
        }
        return result;
    }

    /**
     * Start listening for connections on all the ports that are configured.
     *
     * @param propRoot  Prefix string for all the properties describing the
     *    listeners that are to be started.
     * @param serviceFactory   Object to provide message handler factories for
     *    the new listeners.
     *
     * @return the number of ports that were configured.
     */
    public int startListeners(String propRoot, ServiceFactory serviceFactory) {
        String listenerPropRoot = propRoot;
        int listenerCount = 0;
        
        List<HostDesc> listeners = new LinkedList<HostDesc>();
        while (true) {
            String hostName = myProps.getProperty(listenerPropRoot + ".host");
            if (hostName == null) {
                break;
            }
            HostDesc listener =
                startOneListener(listenerPropRoot, hostName, serviceFactory);
            if (listener != null) {
                listeners.add(listener);
            }
            listenerPropRoot = propRoot + (++listenerCount);
        }

        if (myBrokerHost != null) {
            connectToBroker();
        }
        myListeners = Collections.unmodifiableList(listeners);
        return listenerCount;
    }

    /**
     * Return the application trace object for this server.
     */
    public Trace trace() {
        return tr;
    }

    /**
     * Return the version ID string for this build.
     */
    public String version() {
        return BuildVersion.version;
    }
}

