package org.elkoserver.server.broker;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.json.StaticTypeResolver;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.metadata.LoadDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.HashMapMulti;
import org.elkoserver.util.trace.Trace;

/**
 * Main state data structure in a Broker.
 */
class Broker {
    /** Table for mapping object references in messages. */
    private RefTable myRefTable;

    /** Database for configuration data. */
    private ObjDB myODB;

    /** Server object. */
    private Server myServer;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Registered services.  Maps (service name, protocol) pairs to sets of
        ServiceDesc objects. */
    private HashMapMulti<String, ServiceDesc> myServices;

    /** Clients waiting for services.  Maps service names to sets of
        WaiterForService objects. */
    private HashMapMulti<String, WaiterForService> myWaiters;

    /** Set of currently connected actors. */
    private Set<BrokerActor> myActors;

    /** Set of clients watching servers come & go. */
    private Set<BrokerActor> myServiceWatchers;

    /** Set of clients watching load status. */
    private Set<BrokerActor> myLoadWatchers;

    /** The admin object. */
    private AdminHandler myAdminHandler;

    /** The client object. */
    private ClientHandler myClientHandler;

    /** Table of servers that this broker can launch. */
    private LauncherTable myLauncherTable;

    /**
     * Constructor.
     *
     * @param server  Server object.
     * @param appTrace  Trace object for diagnostics.
     */
    Broker(Server server, Trace appTrace) {
        myServer = server;
        tr = appTrace;

        myRefTable = new RefTable(StaticTypeResolver.theStaticTypeResolver);

        myClientHandler = new ClientHandler(this);
        myRefTable.addRef(myClientHandler);

        myAdminHandler = new AdminHandler(this);
        myRefTable.addRef(myAdminHandler);

        myServices = new HashMapMulti<String, ServiceDesc>();
        myWaiters = new HashMapMulti<String, WaiterForService>();
        myActors = new HashSet<BrokerActor>();
        myServiceWatchers = new HashSet<BrokerActor>();
        myLoadWatchers = new HashSet<BrokerActor>();

        String startModeStr =
            server.props().getProperty("conf.broker.startmode");
        int startMode;
        if (startModeStr == null || startModeStr.equals("initial")) {
            startMode = LauncherTable.START_INITIAL;
        } else if (startModeStr.equals("recover")) {
            startMode = LauncherTable.START_RECOVER;
        } else if (startModeStr.equals("restart")) {
            startMode = LauncherTable.START_RESTART;
        } else {
            tr.errorm("unknown startmode value '" + startModeStr + "'");
            startMode = LauncherTable.START_RECOVER;
        }
        final int finalStartMode = startMode;
        LauncherTable.setTrace(tr);
        myLauncherTable = null;
        myODB = server.openObjectDatabase("conf.broker");
        if (myODB != null) {
            myODB.addClass("launchertable", LauncherTable.class);
            myODB.addClass("launcher", LauncherTable.Launcher.class);
            myODB.getObject("launchertable", null, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj != null) {
                        myLauncherTable = (LauncherTable) obj;
                        myLauncherTable.doStartupLaunches(finalStartMode);
                    } else {
                        tr.warningm("unable to load launcher table");
                        myLauncherTable =
                            new LauncherTable("launchertable",
                                              new LauncherTable.Launcher[]{ });
                    }
                }
            });
        } else {
            tr.warningm("no database specified for launcher configuration");
        }
    }

    /**
     * Get a read-only view of the set of connected actors.
     *
     * @return the set of connected actors.
     */
    Set<BrokerActor> actors() {
        return Collections.unmodifiableSet(myActors);
    }

    /**
     * Add a new actor to the table of connected actors.
     *
     * @param actor  The actor to add.
     */
    void addActor(BrokerActor actor) {
        myActors.add(actor);
    }

    /**
     * Add a new service to the table of registered services.
     *
     * @param service  Description of the service to add.
     */
    void addService(ServiceDesc service) {
        myServices.add(serviceKey(service.service(), service.protocol()),
                       service);
        noteServiceArrival(service);
    }

    /**
     * Make sure the state of the launch table is saved in persistent form.
     */
    void checkpoint() {
        myLauncherTable.checkpoint(myODB);
    }

    /**
     * Get the handler for client messages.
     */
    ClientHandler clientHandler() {
        return myClientHandler;
    }

    /**
     * Obtain this broker's launcher table.
     *
     * @return the launch table.
     */
    LauncherTable launcherTable() {
        return myLauncherTable;
    }

    /**
     * Return an iterable over a set of registered services.
     *
     * @param service  The name of the service sought, or null to get them all.
     * @param protocol  The name of the protocol sought
     *
     * @return an iterable that can be iterated over the registered services
     *    with the given name.
     */
    Iterable<ServiceDesc> services(String service, String protocol) {
        if (service == null) {
            return myServices.values();
        } else {
            return myServices.getMulti(serviceKey(service, protocol));
        }
    }

    /**
     * Take note that a new service is available, in case anybody is waiting.
     *
     * @param service  Description of the new service.
     */
    void noteServiceArrival(ServiceDesc service) {
        List<WaiterForService> deadWaiters =
            new LinkedList<WaiterForService>();
        for (WaiterForService waiter : myWaiters.getMulti(service.service())) {
            if (waiter.noteServiceArrival(service)) {
                deadWaiters.add(waiter);
            }
        }
        for (WaiterForService waiter : deadWaiters) {
            myWaiters.remove(waiter.service(), waiter);
        }
        JSONLiteral msg =
            AdminHandler.msgServiceDesc(myAdminHandler,
                                        service.encodeAsArray(), true);
        for (BrokerActor watcher : myServiceWatchers) {
            watcher.send(msg);
        }
    }

    /**
     * Take note that new load information is available, in case anybody is
     * waiting.
     *
     * @param server  Server for which new load information is available.
     */
    void noteLoadDesc(BrokerActor server) {
        Client client = server.client();
        LoadDesc desc = new LoadDesc(server.label(), client.loadFactor(),
                                     client.providerID());
        JSONLiteral msg =
            AdminHandler.msgLoadDesc(myAdminHandler, desc.encodeAsArray());

        for (BrokerActor watcher : myLoadWatchers) {
            watcher.send(msg);
        }
    }

    /**
     * Take note that a service is has disappeared, in case anybody is
     * watching.
     *
     * @param service  Description of the service that went away.
     */
    void noteServiceDeparture(ServiceDesc service) {
        JSONLiteral msg =
            AdminHandler.msgServiceDesc(myAdminHandler,
                                        service.encodeAsArray(), false);
        for (BrokerActor watcher : myServiceWatchers) {
            watcher.send(msg);
        }
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
    void removeActor(BrokerActor actor) {
        myActors.remove(actor);
    }

    /**
     * Remove a service to the table of registered services.
     *
     * @param service  Description of the service to remove.
     */
    void removeService(ServiceDesc service) {
        myServices.remove(serviceKey(service.service(), service.protocol()),
                          service);
        noteServiceDeparture(service);
    }

    /**
     * Turn a service name and protocol name into a key into the services
     * table.
     *
     * @param service  The service name
     * @param protocol  The protocol name
     *
     * @return a string for indexing the service + protocol pair.
     */
    private String serviceKey(String service, String protocol) {
        return service + "+" + protocol;
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately without cleaning up.
     */
    void shutdownServer(boolean kill) {
        if (!kill) {
            for (BrokerActor actor : new LinkedList<BrokerActor>(myActors)) {
                actor.doDisconnect();
            }
        }
        if (myODB != null) {
            myODB.shutdown();
        }
        myServer.shutdown(kill);
    }

    /**
     * Remove somebody from the collection of clients watching system load.
     *
     * @param who  The new ex-watcher.
     */
    void unwatchLoad(BrokerActor who) {
        myLoadWatchers.remove(who);
    }

    /**
     * Remove somebody from the collection of clients watching services come &
     * go.
     *
     * @param who  The new ex-watcher.
     */
    void unwatchServices(BrokerActor who) {
        myServiceWatchers.remove(who);
    }

    /**
     * Take note that somebody is waiting for a service to appear.
     *
     * @param service  The name of the service sought.
     * @param who  The client who is waiting.
     * @param keepWatching  Flag to keep waiting, even if the service appears.
     * @param timeout  How long to wait before giving up, in seconds (negative
     *    to wait forever).
     * @param failOK  Flag that is true if failure is an option.
     * @param tag  Arbitrary tag that will be sent back with the response, to
     *    match up requests and responses.
     */
    void waitForService(String service, BrokerActor who, boolean keepWatching,
                        int timeout, boolean failOK, String tag)
    {
        WaiterForService waiter =
            new WaiterForService(service, who, keepWatching, timeout, failOK,
                                 tag);
        myWaiters.add(service, waiter);
    }

    /**
     * Object representing one client waiting for one service.
     */
    private class WaiterForService implements TimeoutNoticer {
        /** Name of service waited for. */
        private String myService;

        /** Client who is waiting. */
        private BrokerActor myWaiter;

        /** Flag to keep waiting even after first success. */
        private boolean amKeepWatching;

        /** Flag that there has been at least one success. */
        private boolean amSuccessful;

        /** Tag string for matching requests with responses. */
        private String myTag;

        /** Timeout for giving up after too long. */
        private Timeout myTimeout;

        /**
         * Constructor.
         *
         * @param service  The name of the service sought.
         * @param who  The client who is waiting.
         * @param keepWatching  Flag to keep waiting, even if the service
         *    appears.
         * @param timeout  How long to wait before giving up, in seconds
         *    (negative to wait forever).
         * @param failOK  Flag that is true if failure *is* an option.
         * @param tag  Arbitrary tag that will be sent back with the response,
         *    to match up requests and responses.
         */
        WaiterForService(String service, BrokerActor waiter,
            boolean keepWatching, int timeout, boolean failOK, String tag)
        {
            myService = service;
            myWaiter = waiter;
            amKeepWatching = keepWatching;
            amSuccessful = failOK;
            myTag = tag;
            if (timeout > 0) {
                myTimeout = Timer.theTimer().after(timeout * 1000, this);
            } else {
                myTimeout = null;
            }
        }

        /**
         * Take note of a service having arrived.  Notify the actor who was
         * waiting and record the fact that this was successful.
         *
         * @param service  The service that arrived.
         *
         * @return true if the the caller should stop waiting after this.
         */
        boolean noteServiceArrival(ServiceDesc service) {
            amSuccessful = true;
            if (myTimeout != null && !amKeepWatching) {
                myTimeout.cancel();
                myTimeout = null;
            }
            myClientHandler.findSuccess(myWaiter, service, myTag);
            return !amKeepWatching;
        }

        /**
         * Handle the expiration of the impatience timer.
         */
        public void noticeTimeout() {
            myTimeout = null;
            myWaiters.remove(myService, this);
            if (!amSuccessful) {
                myClientHandler.findFailure(myWaiter, myService, myTag);
            }
        }

        /**
         * Return the service name being waited for.
         */
        String service() {
            return myService;
        }
    }

    /**
     * Add somebody to the collection of clients watching system load.
     *
     * @param who  The new watcher.
     */
    void watchLoad(BrokerActor who) {
        myLoadWatchers.add(who);
    }

    /**
     * Add somebody to the collection of clients watching services come & go.
     *
     * @param who  The new watcher.
     */
    void watchServices(BrokerActor who) {
        myServiceWatchers.add(who);
    }
}
