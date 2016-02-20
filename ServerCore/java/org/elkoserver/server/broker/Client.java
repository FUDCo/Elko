package org.elkoserver.server.broker;

import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.util.HashMapMulti;

/**
 * The client facet of a broker actor.  This object represents the state
 * functionality required when a connected entity is engaging in the client
 * protocol.
 */
class Client {
    /* The broker itself. */
    Broker myBroker;

    /** The actor through whom this facet communicates. */
    private BrokerActor myActor;

    /** Client load factor. */
    private double myLoadFactor;

    /** Services offered by this client. */
    private HashMapMulti<String, ServiceDesc> myServices;

    /** Provider ID associated with this client. */
    private int myProviderID;

    /** Counter for allocating provider IDs.  Starts with 1 because ID 0 is
        reserved for the broker itself. */
    static private int theNextProviderID = 1;

    /**
     * Constructor.
     *
     * @param broker  The broker whose client this is.
     * @param actor  The actor associated with the client.
     */
    Client(Broker broker, BrokerActor actor) {
        myBroker = broker;
        myActor = actor;
        myLoadFactor = 0.0;
        myServices = new HashMapMulti<String, ServiceDesc>();
        myProviderID = theNextProviderID++;
    }

    /**
     * Add a service to the list for this client.
     *
     * @param service  Description of the service to add.
     */
    void addService(ServiceDesc service) {
        service.setProviderID(myProviderID);
        myServices.add(service.service(), service);
        myBroker.addService(service);
    }

    /**
     * Clean up when the client actor disconnects.
     */
    void doDisconnect() {
        for (ServiceDesc service : services()) {
            myBroker.removeService(service);
        }
    }

    /**
     * Return this client's load factor.
     */
    double loadFactor() {
        return myLoadFactor;
    }

    /**
     * Test if a given label matches this client.
     *
     * This will be true if the label is this client's label or one of its
     * host+port strings or its provider ID.
     *
     * @param label  The label to match against.
     */
    boolean matchLabel(String label) {
        if(myActor.label().equals(label)) {
            return true;
        }
        for (ServiceDesc service : myServices.values()) {
            if (service.hostport().equals(label)) {
                return true;
            }
        }
        try {
            if (Integer.parseInt(label) == myProviderID) {
                return true;
            }
        } catch (NumberFormatException e) {
        }
        return false;
    }

    /**
     * Return this client's provider ID.
     */
    int providerID() {
        return myProviderID;
    }

    /**
     * Remove a (group of) service(s) from the list for this client.
     *
     * @param serviceName  Name of the service(s) to remove.
     */
    void removeService(String serviceName) {
        for (ServiceDesc service : myServices.getMulti(serviceName)) {
            myBroker.removeService(service);
        }
        myServices.remove(serviceName);
    }

    /**
     * Return an iterable for the set of services this provider supports.
     */
    Iterable<ServiceDesc> services() {
        return myServices.values();
    }

    /**
     * Set this clients's load factor.
     *
     * @param loadFactor  The value to set it to.
     */
    void setLoadFactor(double loadFactor) {
        if (loadFactor < 0.0) {
            myLoadFactor = 0.0;
        } else {
            myLoadFactor = loadFactor;
        }
    }
}
