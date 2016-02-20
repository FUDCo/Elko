package org.elkoserver.server.context;

import java.util.Iterator;
import java.util.List;
import org.elkoserver.foundation.actor.Actor;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.ConnectionRetrier;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.server.ReinitWatcher;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Live group containing a bundle of related connections to some species of
 * external servers.
 */
abstract class OutboundGroup extends LiveGroup {
    /** The statically configured external servers in this group. */
    private List<HostDesc> myHosts;
    
    /** Flag that the external servers should be located via the broker. */
    private boolean amAutoRegister;

    /** Network manager for making new outbound connections. */
    private NetworkManager myNetworkManager;
    
    /** Server object. */
    private Server myServer;

    /** Message dispatcher for incoming messages on these connections. */
    private MessageDispatcher myDispatcher;

    /** How often to retry connections, in seconds, or -1 for the default. */
    private int myRetryInterval;

    /** Contextor for overall server operations. */
    private Contextor myContextor;
    
    /** Trace object for diagnostics. */
    private Trace tr;

    /** Trace object for controlling message logging. */
    private Trace myMsgTrace;
    
    /**
     * Constructor.
     *
     * @param propRoot  Prefix string for names of config properties pertaining
     *    to this group
     * @param server  Server object.
     * @param contextor  The server contextor.
     * @param hosts  List of HostDesc objects describing external
     *    servers with whom to register.
     * @param appTrace  Trace object for diagnostics.
     */
    OutboundGroup(String propRoot, Server server, Contextor contextor,
                   List<HostDesc> hosts, Trace appTrace)
    {
        myServer = server;
        server.registerReinitWatcher(new ReinitWatcher() {
            public void noteReinit() {
                disconnectHosts();
                connectHosts();
            }
        });

        myNetworkManager = server.networkManager();
        myContextor = contextor;
        myHosts = hosts;
        myDispatcher = new MessageDispatcher(null);
        myDispatcher.addClass(actorClass());
        amAutoRegister = server.props().testProperty(propRoot + ".auto");

        if (server.props().testProperty(propRoot + ".dontlog")) {
            myMsgTrace = Trace.none;
        } else {
            myMsgTrace = Trace.comm;
        }
        myRetryInterval = server.props().intProperty(propRoot + ".retry", -1);

        tr = appTrace;
        
        Iterator<HostDesc> iter = hosts.iterator();
        while (iter.hasNext()) {
            HostDesc host = iter.next();
            if (!host.protocol().equals("tcp")) {
                iter.remove();
                tr.errorm("unknown " + propRoot + " server access protocol '" +
                    host.protocol() + "' for access to " + host.hostPort() +
                    " (configuration ignored)");
            }
        }
    }

    /**
     * Obtain the class of actors in this group.
     *
     * @return this group's actor class.
     */
    abstract Class actorClass();

    /**
     * Open connections to statically configured external servers, try to find
     * out about dynamically configured ones.
     */
    void connectHosts() {
        for (HostDesc host : myHosts) {
            new ConnectionRetrier(host, label(), myNetworkManager,
                                  new HostConnector(host), tr);
        }
        if (amAutoRegister) {
            myServer.findService(service(), new HostFoundHandler(), true);
        }
    }

    /**
     * Open connections to external servers configured via the broker.
     *
     * @param descs  Array of service description objects describing external
     *    servers to connect to.
     */
    private class HostFoundHandler implements ArgRunnable {
        public void run(Object obj) {
            for (ServiceDesc desc : (ServiceDesc[]) obj) {
                if (desc.failure() == null) {
                    HostDesc host = desc.asHostDesc(myRetryInterval);
                    new ConnectionRetrier(host, label(), myNetworkManager,
                                          new HostConnector(host), tr);
                }
            }
        }
    }

    /**
     * Factory class to hold onto host information while attempting to
     * establish an external server connection.
     */
    private class HostConnector implements MessageHandlerFactory {
        /** The external server host being connected to. */
        private HostDesc myHost;

        HostConnector(HostDesc host) {
            myHost = host;
        }

        /**
         * Provide a message handler for a new external server connection.
         *
         * @param connection  The Connection object that was just created.
         */
        public MessageHandler provideMessageHandler(Connection connection) {
            return provideActor(connection, myDispatcher, myHost);
        }
    }

    /**
     * Get this server's contextor.
     *
     * @return the contextor.
     */
    Contextor contextor() {
        return myContextor;
    }

    /**
     * Close connections to all open external servers.
     */
    void disconnectHosts() {
        for (Deliverer member : members()) {
            Actor actor = (Actor) member;
            actor.close();
        }
    }

    /**
     * Test if this group is live, that is, if it corresponds to any actual
     * connections, real or potential.
     *
     * @return true iff there are (or will be) any connections associated with
     *    this group.
     */
    boolean isLive() {
        return myHosts.size() > 0 || amAutoRegister;
    }

    /**
     * Obtain a printable string suitable for tagging this group in log
     * messages and so forth.
     *
     * @return this group's label string.
     */
    abstract String label();

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
    abstract Actor provideActor(Connection connection,
                                MessageDispatcher dispatcher,
                                HostDesc host);

    /**
     * Obtain a broker service string describing the type of service that
     * connections in this group want to connect to.
     *
     * @return a broker service string for this group.
     */
    abstract String service();
}
