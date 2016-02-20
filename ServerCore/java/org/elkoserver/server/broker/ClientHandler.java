package org.elkoserver.server.broker;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;

/**
 * Singleton handler for the broker client protocol.
 *
 * The client protocol consists of these messages:
 *
 *   'find' - Requests location of a service of particular named type,
 *      optionally waiting for the service to become available if one is not
 *      available when the request is received, optionally asking to be
 *      notified of new services of the specified type as they appear.
 *
 *   'load' - Reports the client's current load factor to the broker.
 *
 *   'willserve' - Reports the client's willingness to provide one or more
 *      named services.
 *
 *   'wontserve' - Reports that the client will no longer provide one or more
 *      named services.
 */
class ClientHandler extends BasicProtocolHandler {
    /** The broker for this handler. */
    private Broker myBroker;

    /**
     * Constructor.
     *
     * @param broker  The broker object for this handler.
     */
    ClientHandler(Broker broker) {
        myBroker = broker;
    }

    /**
     * Notify a waiting client that a service it was waiting for has not been
     * found.
     *
     * @param who  The client who was waiting.
     * @param service  The name of the service that was not found.
     * @param tag  Arbitrary tag for matching requests with responses.
     */
    void findFailure(BrokerActor who, String service, String tag) {
        ServiceDesc desc = new ServiceDesc(service, "no such service");
        who.send(msgFind(this, desc.encodeAsArray(), tag));
    }

    /**
     * Notify a waiting client that a service it was waiting for has been
     * found.
     *
     * @param who  The client who was waiting.
     * @param service  The service that was found.
     * @param tag  Arbitrary tag for matching requests with responses.
     */
    void findSuccess(BrokerActor who, ServiceDesc service, String tag) {
        who.send(msgFind(this, service.encodeAsArray(), tag));
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'broker'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "broker";
    }

    /**
     * Handle the 'find' verb.
     *
     * Locate a service for a client.
     *
     * @param from  The client asking to find a service.
     * @param service  The name of the service they are seeking.
     * @param wait  Optional time (default 0) to wait for service to become
     *    available (0 means don't wait, a negative value means wait forever).
     * @param monitor  Optional flag (default false) to keep looking for new
     *    services of the requested name as they appear, even after a reply has
     *    been return.  A value of true is invalid if 'wait' is 0.
     * @param tag  Arbitrary tag that will be sent back with the response, to
     *    help the client match up requests and responses.
     */
    @JSONMethod({ "service", "protocol", "wait", "monitor", "tag" })
    public void find(BrokerActor from, String service, OptString optProtocol,
                     OptInteger optWait, OptBoolean optMonitor,
                     OptString optTag)
    {
        int wait = optWait.value(0);
        boolean monitor = optMonitor.value(false);
        String tag = optTag.value(null);
        String protocol = optProtocol.value("tcp");

        Iterable<ServiceDesc> services = myBroker.services(service, protocol);
        if (!services.iterator().hasNext()) {
            if (wait == 0) {
                findFailure(from, service, tag);
            } else {
                myBroker.waitForService(service, from, monitor, wait, false,
                                        tag);
            }
        } else {
            from.send(msgFind(this, ServiceDesc.encodeArray(services), tag));
            if (monitor) {
                myBroker.waitForService(service, from, true, wait, true, tag);
            }
        }
    }

    /**
     * Handle the 'load' verb.
     *
     * Note a client's load factor.
     *
     * @param from  The client server announcing its load.
     * @param factor  The load factor.
     */
    @JSONMethod({ "factor" })
    public void load(BrokerActor from, double factor)
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        from.client().setLoadFactor(factor);
        myBroker.noteLoadDesc(from);
    }

    /**
     * Test if a service description record what was sent correctly describes a
     * service.
     *
     * @param service  The service to validate.
     *
     * @return true if the given service has a valid description, false if not.
     */
    private boolean validServiceDescription(ServiceDesc service) {
        return service.hostport() != null && service.failure() == null;
    }

    /**
     * Handle the 'willserve' verb.
     *
     * Announce a client willingness to provide one or more services.
     *
     * @param from  The client server announcing its services.
     * @param services  Description(s) of the service(s) offered.
     */
    @JSONMethod({ "services" })
    public void willserve(BrokerActor from, ServiceDesc services[])
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        if (services != null) {
            for (ServiceDesc service : services) {
                if (validServiceDescription(service)) {
                    from.client().addService(service);
                }
            }
        }
    }

    /**
     * Handle the 'wontserve' verb.
     *
     * Announce a client server's ceasing to provide one or more services.
     *
     * @param from  The client server retracting its services.
     * @param services  Name(s) of the service(s) no longer offered.
     */
    @JSONMethod({ "services" })
    public void wontserve(BrokerActor from, String services[])
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        if (services != null) {
            for (String service : services) {
                from.client().removeService(service);
            }
        }
    }

    /**
     * Generate a 'find' message.
     */
    static JSONLiteral msgFind(Referenceable target, JSONLiteralArray desc,
                               String tag)
    {
        JSONLiteral msg = new JSONLiteral(target, "find");
        msg.addParameter("desc", desc);
        msg.addParameterOpt("tag", tag);
        msg.finish();
        return msg;
    }
}
