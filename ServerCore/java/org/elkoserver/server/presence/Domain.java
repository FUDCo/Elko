package org.elkoserver.server.presence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class Domain {
    static private int theNextIndex = 0;
    static private ArrayList<Domain> theDomains = new ArrayList<Domain>();

    private int myIndex;
    private String myName;
    private Map<String, PresenceActor> mySubscribers;

    Domain(String name) {
        myIndex = theNextIndex++;
        myName = name;
        mySubscribers = new HashMap<String, PresenceActor>();
        theDomains.add(myIndex, this);
    }

    static Domain domain(int index) {
        return theDomains.get(index);
    }

    int index() {
        return myIndex;
    }

    static int maxIndex() {
        return theNextIndex;
    }

    String name() {
        return myName;
    }

    PresenceActor subscriber(String context) {
        return mySubscribers.get(context);
    }

    void addSubscriber(String context, PresenceActor client) {
        mySubscribers.put(context, client);
    }

    void removeClient(PresenceActor client) {
        Iterator<PresenceActor> iter = mySubscribers.values().iterator();
        while (iter.hasNext()) {
            PresenceActor subscriber = iter.next();
            if (subscriber == client) {
                iter.remove();
            }
        }
    }

    void removeSubscriber(String context) {
        mySubscribers.remove(context);
    }
}
