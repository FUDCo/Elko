package org.elkoserver.server.broker;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.metadata.LoadDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;
import java.util.LinkedList;
import java.util.List;

/**
 * Singleton handler for the broker 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 *   'loaddesc' - Requests a description of the load on each of the servers
 *      that the broker knows about.
 *
 *   'launch' - Command this broker to startup a new server process.
 *
 *   'launcherdesc' - Request a description of the launchers this broker is
 *      configured with.
 *
 *   'reinit' - Requests the broker to order the reinitialization of zero or
 *      more of the servers it knows about, and, optionally, itself.
 *
 *   'servicedesc' - Requests a description of available services that the
 *      broker knows about, optionally limiting the scope of the query to
 *      particular service name classes.
 *
 *   'shutdown' - Requests the broker to order the shut down of zero or more of
 *     the servers it knows about, and, optionally, itself.  Also has an option
 *     to force abrupt termination.
 *
 *   'watch' - Requests the broker to send, or stop sending, notifications
 *      about services and/or load information as information about these
 *      arrives from the various servers.
 */
class AdminHandler extends BasicProtocolHandler {

    /** The broker for this handler. */
    private Broker myBroker;

    /**
     * Constructor.
     *
     * @param broker  The broker object for this handler.
     */
    AdminHandler(Broker broker) {
        myBroker = broker;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "admin";
    }

    /**
     * Send load information to a client.
     *
     * @param who  Who to send the information to.
     * @param what  Server to send them information about, or null for all.
     */
    private void sendLoadDesc(BrokerActor who, String what) {
        JSONLiteralArray array = new JSONLiteralArray();
        for (BrokerActor actor : myBroker.actors()) {
            Client client = actor.client();
            if (client != null) {
                if (what == null || client.matchLabel(what)) {
                    LoadDesc desc =
                        new LoadDesc(actor.label(), client.loadFactor(),
                                     client.providerID());
                    array.addElement(desc);
                }
            }
        }
        array.finish();
        who.send(msgLoadDesc(this, array));
    }

    /**
     * Send service information to a client.
     *
     * @param who  Who to send the information to.
     * @param service  Service to send them information about, or null for all.
     * @param protocol  Protocol to send service information about.
     */
    private void sendServiceDesc(BrokerActor who, String service,
                                 String protocol)
    {
        Iterable<ServiceDesc> services = myBroker.services(service, protocol);
        who.send(msgServiceDesc(this, ServiceDesc.encodeArray(services),
                                true));
    }

    /**
     * Handle the 'launch' verb.
     *
     * Request the broker to start another server process by invoking one of
     * the broker's configured launchers.
     *
     * @param from  The administrator who is commanding this.
     * @param name  The name of component launcher configuration
     */
    @JSONMethod({ "name" })
    public void launch(BrokerActor from, String name)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String status = myBroker.launcherTable().launch(name);
        if (status == null) {
            status = "start " + name;
        }
        myBroker.checkpoint();
        from.send(msgLaunch(this, status));
    }

    /**
     * Handle the 'launcherdesc' verb.
     *
     * Request information about the launchers this broker is configured with.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    public void launcherdesc(BrokerActor from) throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        from.send(msgLauncherDesc(this,
                                  myBroker.launcherTable().encodeAsArray()));
    }

    /**
     * Handle the 'loaddesc' verb.
     *
     * Request information about the load on connected servers.
     *
     * @param from  The administrator asking for the information.
     * @param server  The name of the server of interest (or null for all).
     */
    @JSONMethod({ "server" })
    public void loaddesc(BrokerActor from, OptString optServer)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        sendLoadDesc(from, optServer.value(null));
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that one or more connected servers be reset.
     *
     * @param from  The administrator sending the message.
     * @param server  The connected server to be re-init'ed ("all" for all of
     *    them, or null for none of them).
     * @param self  true if the this broker itself should be re-init'ed.
     */
    @JSONMethod({ "server", "self" })
    public void reinit(BrokerActor from, OptString optServer,
                       OptBoolean optSelf)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String serverName = optServer.value(null);
        if (serverName != null) {
            JSONLiteral msg = msgReinit(myBroker.clientHandler());
            for (BrokerActor actor : myBroker.actors()) {
                Client client = actor.client();
                if (client != null) {
                    if (serverName.equals("all") ||
                            client.matchLabel(serverName)) {
                        actor.send(msg);
                    }
                }
            }
        }
        if (optSelf.value(false)) {
            myBroker.reinitServer();
        }
    }

    /**
     * Handle the 'servicedesc' verb.
     *
     * Request information about connected servers.
     *
     * @param from  The administrator asking for the information.
     * @param service  The name of the service of interest (or null for all).
     * @param protocol The name of the protocol of interest
     */
    @JSONMethod({ "service", "protocol" })
    public void servicedesc(BrokerActor from, OptString service,
                            OptString protocol)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        sendServiceDesc(from, service.value(null), protocol.value("tcp"));
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that one or more connected servers be shut down.
     *
     * @param from  The administrator sending the message.
     * @param server  The connecte server to be shut down, if any ("all" for
     *    all of them, null for none of them).
     * @param self  true if this broker itself should be shut down.
     * @param kill  true if the shutdown should happen immediately without
     *    waiting for orderly shutdown to complete.
     * @param cluster  true if this is part of a cluster shutdown that should
     *    not alter component run settings.
     */
    @JSONMethod({ "server", "self", "kill", "cluster" })
    public void shutdown(BrokerActor from, OptString optServer,
                         OptBoolean optSelf, OptBoolean optKill,
                         OptBoolean optCluster)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String serverName = optServer.value(null);
        boolean componentShutdown = !optCluster.value(false);
        boolean kill = optKill.value(false);
        if (serverName != null) {
            JSONLiteral msg = msgShutdown(myBroker.clientHandler(), kill);
            List<BrokerActor> actorsToKill =
                new LinkedList<BrokerActor>(myBroker.actors());
            for (BrokerActor actor : actorsToKill) {
                Client client = actor.client();
                if (client != null) {
                    if (serverName.equals("all") ||
                            client.matchLabel(serverName)) {
                        if (componentShutdown) {
                            myBroker.launcherTable().
                                setRunSettingOn(serverName, false);
                        }
                        actor.send(msg);
                    }
                }
            }
            kill = false; /* Need to ignore the kill flag in this case, because
                             abrupt shutdown would terminate this server before
                             the shutdown messages to the *other* servers got
                             sent. */
        }
        myBroker.checkpoint();
        if (optSelf.value(false)) {
            myBroker.shutdownServer(kill);
        }
    }

    /**
     * Handle the 'watch' verb.
     *
     * Request notification about changes to information that the broker knows.
     *
     * @param from  The administrator asking for the information.
     * @param services  Flag indicating whether sender wants to be notified
     *    about services coming or going (true=>yes, false=>no,
     *    omitted=>leave as currently set).
     * @param load  Flag indicating whether sender wants to be notified
     *    about server load status changes (true=>yes, false=>no,
     *    omitted=>leave as currently set).
     */
    @JSONMethod({ "services", "load" })
    public void watch(BrokerActor from, OptBoolean services, OptBoolean load)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        if (services.present()) {
            if (services.value()) {
                sendServiceDesc(from, null, null);
                myBroker.watchServices(from);
            } else {
                myBroker.unwatchServices(from);
            }
        }
        if (load.present()) {
            if (load.value()) {
                sendLoadDesc(from, null);
                myBroker.watchLoad(from);
            } else {
                myBroker.unwatchLoad(from);
            }
        }
    }

    /**
     * Generate a 'launch' message.
     */
    static JSONLiteral msgLaunch(Referenceable target, String status) {
        JSONLiteral msg = new JSONLiteral(target, "launch");
        msg.addParameter("status", status);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'launcherdesc' message.
     */
    static JSONLiteral msgLauncherDesc(Referenceable target,
                                       JSONLiteralArray launchers)
    {
        JSONLiteral msg = new JSONLiteral(target, "launcherdesc");
        msg.addParameter("launchers", launchers);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'loaddesc' message.
     */
    static JSONLiteral msgLoadDesc(Referenceable target, JSONLiteralArray desc)
    {
        JSONLiteral msg = new JSONLiteral(target, "loaddesc");
        msg.addParameter("desc", desc);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'reinit' message.
     */
    static JSONLiteral msgReinit(Referenceable target) {
        JSONLiteral msg = new JSONLiteral(target, "reinit");
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'servicedesc' message.
     */
    static JSONLiteral msgServiceDesc(Referenceable target,
                                      JSONLiteralArray desc, boolean on)
    {
        JSONLiteral msg = new JSONLiteral(target, "servicedesc");
        msg.addParameter("desc", desc);
        msg.addParameter("on", on);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'shutdown' message.
     */
    static JSONLiteral msgShutdown(Referenceable target, boolean kill) {
        JSONLiteral msg = new JSONLiteral(target, "shutdown");
        if (kill) {
            msg.addParameter("kill", kill);
        }
        msg.finish();
        return msg;
    }
}
