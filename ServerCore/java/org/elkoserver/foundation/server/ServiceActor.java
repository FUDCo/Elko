package org.elkoserver.foundation.server;

import java.util.LinkedList;
import java.util.List;
import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.actor.RoutingActor;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor for a connection to an external service.
 */
public class ServiceActor extends RoutingActor
{
    /** Optional convenience label for logging and such. */
    private String myLabel;

    /** List of service links using this actor. */
    private LinkedList<ServiceLink> myServiceLinks;

    /** The server in which we are running. */
    private Server myServer;

    /** Provider ID of the host we are connected to. */
    private int myProviderID;

    /** Trace object for diagnostics. */
    private Trace tr;

    /**
     * Constructor.
     *
     * @param connection  The connection for talking to this actor.
     * @param refTable  Ref table for dispatching messaes sent here.
     * @param desc  Service descriptor for the server being connected to
     * @param server  The server we are calling from
     */
    ServiceActor(Connection connection, RefTable refTable, ServiceDesc desc,
                 Server server)
    {
        super(connection, refTable);
        myServiceLinks = new LinkedList<ServiceLink>();
        myServer = server;
        send(msgAuth("workshop", desc.auth(), server.serverName()));
        myLabel = "workshop-" + desc.hostport();
        myProviderID = desc.providerID();
        tr = server.trace();
    }

    /**
     * Add a service link to the list of links known to be dependent upon this
     * actor's connection.
     *
     * @param link  The new link to add.
     */
    void addLink(ServiceLink link) {
        myServiceLinks.add(link);
        link.connectActor(this);
    }

    /**
     * Handle loss of connection from the actor.
     *
     * @param connection  The connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        tr.eventm(this + " connection died: " + connection + " " + reason);
        myServer.serviceActorDied(this);
        for (ServiceLink link : myServiceLinks) {
            link.actorDied();
        }
    }

    /**
     * Return this actor's label.
     */
    String label() {
        return myLabel;
    }

    /**
     * Get the Broker-issed provider ID of the server at the other end of
     * this actor's connection.
     *
     * @return this actor's provider ID.
     */
    int providerID() {
        return myProviderID;
    }

    /**
     * Obtain a list of the services currently linked to through this actor.
     *
     * @return a list of this actor's service links.
     */
    List<ServiceLink> serviceLinks() {
        return myServiceLinks;
    }

    /**
     * @return a printable representation of this actor.
     */
    public String toString() {
        if (myLabel == null) {
            return super.toString();
        } else {
            return myLabel;
        }
    }
}
