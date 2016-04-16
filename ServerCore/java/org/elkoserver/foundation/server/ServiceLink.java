package org.elkoserver.foundation.server;

import java.util.LinkedList;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.json.JSONLiteral;

/**
 * Class representing a connection to a service rather than to a specific
 * client or server.  A service link can be maintained across loss of
 * connectivity to specific connected entities, at the cost of allowing some
 * of the state associated with the connection be ephemeral.
 */
public class ServiceLink implements Deliverer
{
    /** The actor this link uses to communicate with its service, or null if
        the connection is currently not up. */
    private ServiceActor myActor;

    /** The name of the service this link connects to.*/
    private String myService;

    /** The server from which this link connects outward. */
    private Server myServer;

    /** A list of messages awaiting transmission, accumulated when the
     * connection is down. */
    private LinkedList<JSONLiteral> myPendingMessages;

    /** Flag indicating that service connection setup failed. */
    private boolean amFailed;

    /**
     * Construct a new service link.  Initially, the link has no outbound
     * connection.
     *
     * @param service  The name of the service this link will connect to.
     * @param server  The server this link is working for.
     */
    ServiceLink(String service, Server server) {
        myService = service;
        myServer = server;
        myActor = null;
        myPendingMessages = null;
        amFailed = false;
    }

    /**
     * Obtain the actor this link is currently using for message sends.
     *
     * @return this link's associated actor.
     */
    ServiceActor actor() {
        return myActor;
    }

    /**
     * Take note that the actor this link was dependent on lost its connection.
     * Begin re-establishing the connection, and meanwhile start queuing
     * messages.
     */
    void actorDied() {
        myActor = null;
        if (!amFailed) {
            myServer.reestablishServiceConnection(myService, this);
        }
    }

    /**
     * Establish this link's connection (or re-establish it after it has been
     * lost).  Any messages that have been queued up in the interim will be
     * sent.
     *
     * @param actor  The new actor to use.
     */
    void connectActor(ServiceActor actor) {
        myActor = actor;
        while (myPendingMessages != null) {
            JSONLiteral message = myPendingMessages.peek();
            myActor.send(message);
            if (myActor == null) {
                break;
            }
            myPendingMessages.removeFirst();
            if (myPendingMessages.isEmpty()) {
                myPendingMessages = null;
            }
        }
    }

    /**
     * Mark this service link as failed.
     */
    void fail() {
        amFailed = true;
        if (myPendingMessages != null && !myPendingMessages.isEmpty()) {
            myServer.trace().errorm(this +
                                    " failed with pending outbound messages");
        }
    }

    /**
     * Send a message over this link to the entity at the other end.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        if (amFailed) {
            throw new RuntimeException("message send on failed " + this);
        }
        if (myActor == null) {
            if (myPendingMessages == null) {
                myPendingMessages = new LinkedList<JSONLiteral>();
            }
            myPendingMessages.addLast(message);
        } else {
            myActor.send(message);
        }
    }

    /**
     * Obtain the name of the service that this link connects to.
     *
     * @return the service name associated with this link.
     */
    String service() {
        return myService;
    }

    public String toString() {
        return "ServiceLink to " + myService;
    }
}
