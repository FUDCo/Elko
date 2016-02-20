package org.elkoserver.server.director;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elkoserver.util.HashMapMulti;
import org.elkoserver.util.trace.Trace;

/**
 * The provider facet of a director actor.  This object represents the state
 * functionality required when a connected entity is engaging in the provider
 * protocol.
 */
class Provider implements Comparable {
    /** The director itself. */
    private Director myDirector;

    /** The actor through whom this facet communicates. */
    private DirectorActor myActor;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Provider load factor. */
    private double myLoadFactor;

    /** Names of context families served. */
    private Set<String> myServices;

    /** Names of restricted context families served. */
    private Set<String> myRestrictedServices;

    /** Number of users provider is willing to serve (-1 for no limit). */
    private int myCapacity;

    /** Number of users currently being served. */
    private int myUserCount;

    /** Host+port for this provider, by protocol. */
    private Map<String, String> myHostPorts;

    /** Contexts currently open, by name. */
    private Map<String, OpenContext> myContexts;

    /** Context clone sets current open, by name. */
    private HashMapMulti<String, OpenContext> myCloneSets;

    /** Ordinal for consistent non-equality when load factors are equal. */
    private int myOrdinal;

    /** Counter for assigning ordinal values to new providers. */
    private static int theNextOrdinal = 0;

    /**
     * Constructor.
     *
     * @param director  The director that is tracking the provider.
     * @param actor  The actor associated with the provider.
     * @param appTrace  Trace object for diagnostics.
     */
    Provider(Director director, DirectorActor actor, Trace appTrace) {
        myDirector = director;
        myActor = actor;
        tr = appTrace;
        myUserCount = 0;
        myOrdinal = theNextOrdinal++;
        myCapacity = -1;
        myCloneSets = new HashMapMulti<String, OpenContext>();
        myContexts = new HashMap<String, OpenContext>();
        myHostPorts = new HashMap<String, String>();
        myLoadFactor = 0.0;
        myServices = new HashSet<String>();
        myRestrictedServices = new HashSet<String>();
        myDirector.addProvider(this);
    }

    public String toString() {
        return "P(" + myOrdinal + ")";
    }

    /**
     * Get the actor associated with this provider.
     *
     * @return the actor through whom this provider communicates.
     */
    DirectorActor actor() {
        return myActor;
    }

    /**
     * Compare this provider to another for sorting (comparison is by load
     * factor).
     */
    public int compareTo(Object other) {
        Provider otherProvider = (Provider) other;
        double diff = myLoadFactor - otherProvider.myLoadFactor;
        if (diff < 0.0) {
            return -1;
        } else if (diff > 0.0) {
            return 1;
        } else {
            return myOrdinal - otherProvider.myOrdinal;
        }
    }

    /**
     * Add a protocol to the list of protocols this provider will server with.
     *
     * @param protocol  Name of the protocol to add.
     * @param hostPort  Host+port for reaching this provider using 'protocol'.
     */
    void addProtocol(String protocol, String hostPort) {
        myHostPorts.put(protocol, hostPort);
    }

    /**
     * Add a service to the list for this provider.
     *
     * @param contextFamily  The name of the context family to add.
     * @param capacity  The capacity of the provider, or -1 for no limit.
     */
    void addService(String contextFamily, int capacity, boolean restricted) {
        myServices.add(contextFamily);
        if (restricted) {
            myRestrictedServices.add(contextFamily);
        }
        myCapacity = capacity;
    }

    /**
     * Obtain this provider's capacity.
     *
     * @return the number of users this provider is willing to serve, or -1 if
     *    there is no limit.
     */
    int capacity() {
        return myCapacity;
    }

    /**
     * Get a read-only view of the contexts currently opened by this provider.
     *
     * @return a collection of this provider's open contexts.
     */
    Collection<OpenContext> contexts() {
        return Collections.unmodifiableCollection(myContexts.values());
    }

    /**
     * Clean up when the provider actor disconnects.
     */
    void doDisconnect() {
        for (OpenContext context : myContexts.values()) {
            myDirector.removeContext(context);
        }
        myDirector.removeProvider(this);
    }

    /**
     * Return the "key" for comparing this provider against another for
     * purposes of duplicate elimination.  The key is the host+port string for
     * the protocol whose name string is lexically the highest.
     */
    String dupKey() {
        String candidateProtocol = "";
        for (String protocol : myHostPorts.keySet()) {
            if (protocol.compareTo(candidateProtocol) > 0) {
                candidateProtocol = protocol;
            }
        }
        return myHostPorts.get(candidateProtocol);
    }

    /**
     * Test if this provider is running at least one clone of some context.
     *
     * @param contextName  Name of the clone set of interest.
     *
     * @return true if the named clone set is on this provider somewhere.
     */
    boolean hasClone(String contextName) {
        return !myCloneSets.getMulti(contextName).isEmpty();
    }

    /**
     * Test if a user is in some context on this provider.
     *
     * @param user  The name of the user to look for.
     *
     * @return true if the named user is on this provider somewhere.
     */
    boolean hasUser(String user) {
        for (OpenContext context : myContexts.values()) {
            if (context.hasUser(user)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the host+port for reaching this provider via a given protocol.
     *
     * @param protocol  The protocol sought.
     *
     * @return the host+port for to talk to this provider using 'protocol'.
     */
    String hostPort(String protocol) {
        return myHostPorts.get(protocol);
    }

    /**
     * Get a read-only view of the set of host+ports this provider supports.
     *
     * @return a collection of this provider's host+ports.
     */
    Collection<String> hostPorts() {
        return Collections.unmodifiableCollection(myHostPorts.values());
    }

    /**
     * Test if this provider is unable to accept more users.
     *
     * @return true if this provider has reached its capacity limit.
     */
    boolean isFull() {
        return myCapacity >= 0 && myUserCount >= myCapacity;
    }

    /**
     * Return this provider's load factor.
     */
    double loadFactor() {
        return myLoadFactor;
    }

    /**
     * Test if a given label matches this provider.
     *
     * This will be true if the label is this provider's label or one of its
     * host+port strings.
     *
     * @param label  The label to match against.
     */
    boolean matchLabel(String label) {
        return myActor.label().equals(label) ||
            myHostPorts.containsValue(label);
    }

    /**
     * Take note that this provider has closed a context.
     *
     * @param name  The context that was closed.
     */
    void noteContextClose(String name) {
        OpenContext context = myContexts.get(name);
        if (context != null) {
            myDirector.removeContext(context);
            myContexts.remove(name);
            if (context.isClone()) {
                myCloneSets.remove(context.cloneSetName(), context);
            }
        } else {
            context = myDirector.getContext(name);
            if (context != null) {
                tr.eventi(myActor + " reported closure of context " + name +
                          " belonging to another provider (likely dup)");
            } else {
                tr.eventi(myActor +
                          " reported closure of non-existent context " + name);
            }
        }
    }

    /**
     * Open or close a context's gate, controlling entry of new users.
     *
     * @param name  The context whose gate is being controlled.
     * @param open  True if the gate is being opened, false if closed.
     * @param reason  String indicating why the gate is being closed; ignored
     *    if the gate is being opened.
     */
    void noteContextGateSetting(String name, boolean open, String reason) {
        OpenContext context = myContexts.get(name);
        if (context != null) {
            if (open) {
                context.openGate();
            } else {
                context.closeGate(reason);
            }
        } else {
            tr.eventi(myActor + " set gate for non-existent context " + name);
        }
    }

    /**
     * Take note that this provider has opened a context.
     *
     * @param name  The context that was opened.
     * @param mine  true if this director is the one who asked the context to
     *    open (for use in closing duplicate opens).
     * @param maxCapacity  The maximum user capacity for the context.
     * @param baseCapacity  The base capacity for the (clone) context.
     * @param restricted  true if this context is entry restricted
     */
    void noteContextOpen(String name, boolean mine, int maxCapacity,
                         int baseCapacity, boolean restricted)
    {
        OpenContext newContext = new OpenContext(this, name, mine, maxCapacity,
                                                 baseCapacity, restricted);
        OpenContext oldContext = myDirector.getContext(name);
        if (oldContext != null) {
            OpenContext dupToClose = oldContext.pickDupToClose(newContext);
            if (dupToClose.isMine()) {
                dupToClose.provider().actor().send(
                    AdminHandler.msgClose(myDirector.providerHandler(),
                                          dupToClose.name(), null, true));
            }
            if (dupToClose == newContext) {
                newContext = null;
            } else {
                myDirector.removeContext(oldContext);
            }
        }
        if (newContext != null) {
            myDirector.addContext(newContext);
            myContexts.put(name, newContext);
            if (newContext.isClone()) {
                myCloneSets.add(newContext.cloneSetName(), newContext);
            }
        }
    }

    /**
     * Take note that a user has entered one of this provider's contexts.
     *
     * @param contextName  The name of the context entered.
     * @param userName  The name of the user who entered it.
     */
    void noteUserEntry(String contextName, String userName) {
        OpenContext context = myContexts.get(contextName);
        if (context != null) {
            context.addUser(userName);
            myDirector.addUser(userName, context);
            ++myUserCount;
        } else {
            tr.errorm(myActor + " reported entry of " + userName +
                      " to non-existent context " + contextName);
        }
    }

    /**
     * Take note that a user has exited one of this provider's contexts.
     *
     * @param contextName  The name of the context exited.
     * @param userName  The name of the user who exited from it.
     */
    void noteUserExit(String contextName, String userName) {
        OpenContext context = myContexts.get(contextName);
        if (context != null) {
            myDirector.removeUser(userName, context);
            context.removeUser(userName);
            --myUserCount;
        } else {
            tr.errorm(myActor + " reported exit of " + userName +
                      " from non-existent context " + contextName);
        }
    }

    /**
     * Get a read-only view of the set of protocols this provider supports.
     *
     * @return a set of this provider's protocols.
     */
    Set<String> protocols() {
        return Collections.unmodifiableSet(myHostPorts.keySet());
    }

    /**
     * Get a read-only view of the set of services this provider supports.
     *
     * @return a set of this provider's services.
     */
    Set<String> services() {
        return Collections.unmodifiableSet(myServices);
    }

    /**
     * Set this provider's load factor.
     *
     * @param loadFactor  The value to set it to.
     */
    void setLoadFactor(double loadFactor) {
        myDirector.removeProvider(this);
        if (loadFactor < 0.0) {
            myLoadFactor = 0.0;
        } else {
            myLoadFactor = loadFactor;
        }
        myDirector.addProvider(this);
    }

    /**
     * Test if this provider will provide a particular service.
     *
     * @param service  Name of the service desired.
     * @param protocol  Protocol desired to access it by.
     * @param internal  Flag indicating a request from within the server farm
     *
     * @return true if this provider will serve 'service' using 'protocol'.
     */
    boolean willServe(String service, String protocol, boolean isInternal) {
        if (!isInternal && myRestrictedServices.contains(service)) {
            return false;
        } else {
            return myServices.contains(service) &&
                myHostPorts.containsKey(protocol) &&
                !isFull();
        }
    }
}
