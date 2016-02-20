package org.elkoserver.server.presence;

import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Information we are keeping track of with respect to an online user.  In
 * particular, we track the user's presences: all the contexts in which the
 * user is current present.
 */
class ActiveUser {
    /** The user's identity. */
    private String myRef;

    /** Number of domais that are loaded. */
    private int myDomainLoadCount;

    /** Current online presences of this user, stored in the form of an array
        of the refs of the contexts in which they are present. */
    private String[] myPresences;

    /** The members of the user's social graph, or null if not yet loaded, by
        domain index.  Note: these are called 'friends' because the word is
        short and the structural meaning is clear, but they might not actually
        be friends per se (i.e., they could be rivals or enemies). */
    private ArrayList<Iterable<String>> myFriendsByDomain;

    /** Optional user metadata for this user. */
    private JSONObject myMetadata;

    /**
     * Constructor.
     *
     * @param ref  The ref associated with the client.
     */
    ActiveUser(String ref) {
        myRef = ref;
        myPresences = null;
        myFriendsByDomain = null;
        myDomainLoadCount = 0;
    }

    void noteMetadata(JSONObject metadata) {
        myMetadata = metadata;
    }

    /**
     * Add a new presence for this user to the collection we are following.
     *
     * @param context  The context the user is in
     * @param master  The presence server master instance.
     */
    void addPresence(String context, PresenceServer master) {
        /* The collection of presences is kept in a simple array, rather than
           an indexed collection object such as a map, because a given active
           user is likely to have very few of these (i.e., in the vast, vast
           majority of cases, typically just one), meaning that locating
           entries by linear search of the array is quite reasonable and does
           not justify the additional storage overhead of a more complicated
           data structure. */
        if (myPresences == null) {
            /* Start with a single element array, to hold the one presence. */
            myPresences = new String[1];
            myPresences[0] = context;
        } else {
            /* If there are existing presences, scan the presences array for a
               null entry, so that we can reuse an existing slot in the array
               rather than reallocating and copying it. */
            int nullIdx = -1;
            for (int i = 0; i < myPresences.length; ++i) {
                if (myPresences[i] == null) {
                    /* A vacant slot! remember it in case we need one. */
                    nullIdx = i;
                    break;
                }
            }
            if (nullIdx >= 0) {
                /* If we found a vacant slot, put the new entry there. */
                myPresences[nullIdx] = context;
            } else {
                /* Otherwise, enlarge the presences array by one and put the
                   new entry at the end. We expand by just one because, as with
                   the justification for using an array in the first place, the
                   size is unlikely to grow further. */
                String[] newPresences = new String[myPresences.length + 1];
                System.arraycopy(myPresences, 0, newPresences, 0,
                                 myPresences.length);
                newPresences[myPresences.length] = context;
                myPresences = newPresences;
            }
        }
        /* If the social graph is loaded, perform the notifications associated
           with a new presence's arrival.  If it's not loaded, these
           notifications will happen later, when the graph data arrives. */
        if (graphsAreReady()) {
            notifyFriendsAboutMe(true, context, master);
            notifyMeAboutFriends(context, master);
        }
    }

    /**
     * Create a representation of this user's social graph connections,
     * suitable for delivery in a server state dump.  This is intended for
     * debugging and testing; the format of the data object produced should
     * not be regarded as stable!
     *
     * @return a JSON literal array encoding this user's social graph.
     */
    JSONLiteralArray encodeFriendsDump() {
        JSONLiteralArray result = new JSONLiteralArray();
        if (graphsAreReady()) {
            for (int i = 0; i < myFriendsByDomain.size(); ++i) {
                JSONLiteral graph = new JSONLiteral();
                graph.addParameter("domain", Domain.domain(i).name());
                graph.addParameter("friends", myFriendsByDomain.get(i));
                graph.finish();
                result.addElement(graph);
            }
        }
        result.finish();
        return result;
    }

    /**
     * Test if the social graph data for this user have been loaded.
     *
     * @return true iff this user's social graph data are immediately
     *    available.
     */
    boolean graphsAreReady() {
        return myFriendsByDomain != null &&
               myDomainLoadCount == Domain.maxIndex();
    }

    /**
     * Obtain the array of presence contexts, suitable for delivery in a server
     * state dump.  This is intended for debugging and testing, so we are not
     * being careful to put the return value in a read-only wrapper. Take care.
     *
     * @return a string array containing this user's current presences.
     */
    String[] presences() {
        return myPresences;
    }

    /**
     * Notify this user's friends about this user's change in presence.
     * Actually, we don't notify the friends directly; rather, we notify the
     * clients (i.e., context servers) the friends are on: one message to each
     * client with at least one such user.  Moreover, we only send a
     * notification for a given friend if the friend is in a context that has
     * subscribed to the domain of the friend's relationship to this user.
     *
     * @param on  true if the user came online, false if they went offline
     * @param userContext  The context that the user entered or exited
     * @param master  The presence server master instance.
     */
    void notifyFriendsAboutMe(boolean on, String userContext,
                              PresenceServer master)
    {
        /* For each client, a per domain collection:
           For each domain, a per context collection:
           For each context, a list of users */
        Map<PresenceActor, Map<Domain, Map<String, List<String>>>> tell =
          new HashMap<PresenceActor, Map<Domain, Map<String, List<String>>>>();

        for (int i = 0; i < myFriendsByDomain.size(); ++i) {
            Iterable<String> friends = myFriendsByDomain.get(i);
            if (friends != null) {
                Domain domain = Domain.domain(i);
                for (String friendName : friends) {
                    ActiveUser friend = master.getActiveUser(friendName);
                    if (friend != null) {
                        if (friend.myPresences != null) {
                            for (String context : friend.myPresences) {
                                if (context != null) {
                                    PresenceActor client =
                                        domain.subscriber(context);
                                    if (client != null) {
                                        Map<Domain, Map<String, List<String>>> dtell =
                                            tell.get(client);
                                        if (dtell == null) {
                                            dtell =
                                                new HashMap<Domain,
                                                            Map<String,
                                                                List<String>>>();
                                            tell.put(client, dtell);
                                        }
                                        Map<String, List<String>> ctell =
                                            dtell.get(domain);
                                        if (ctell == null) {
                                            ctell =
                                                new HashMap<String,
                                                            List<String>>();
                                            dtell.put(domain, ctell);
                                        }
                                        List<String> nameList =
                                            ctell.get(context);
                                        if (nameList == null) {
                                            nameList =
                                                new LinkedList<String>();
                                            ctell.put(context, nameList);
                                        }
                                        nameList.add(friend.ref());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Map.Entry<PresenceActor, Map<Domain, Map<String, List<String>>>> entry :
                 tell.entrySet()) {
            entry.getKey().send(msgUserToGroup(myRef, myMetadata, userContext,
                                               on, entry.getValue(), master));
        }
    }

    /**
     * Notify the user's presence (that presence's context server, actually)
     * about the online presences of the other users in their social graph
     * who are online now.  Note that this notification only happens if the
     * user is in a context that has subscribed to one or more of the user's
     * friend domains.
     *
     * @param userContext  The context of my user presence which is to receive
     *    the notification
     * @param master  The presence server master instance.
     */
    void notifyMeAboutFriends(String userContext, PresenceServer master) {
        Map<Domain, List<FriendInfo>> friends =
            new HashMap<Domain, List<FriendInfo>>();
        PresenceActor client = null;
        for (int i = 0; i < Domain.maxIndex(); ++i) {
            if (myFriendsByDomain.get(i) != null) {
                List<FriendInfo> friendList = new LinkedList<FriendInfo>();
                Domain domain = Domain.domain(i);
                client = domain.subscriber(userContext);
                if (client != null) {
                    for (String friendName : myFriendsByDomain.get(i)) {
                        ActiveUser friend = master.getActiveUser(friendName);
                        if (friend != null) {
                            if (friend.myPresences != null) {
                                for (String context : friend.myPresences) {
                                    if (context != null) {
                                        friendList.add(
                                            new FriendInfo(friendName,
                                                           friend.myMetadata,
                                                           context,
                                                           master.getContextMetadata(context)));
                                    }
                                }
                            }
                        }
                    }
                    friends.put(domain, friendList);
                }
            }
        }
        if (!friends.isEmpty() && client != null) {
            /* Only send me a message telling me about my friend's presences if
               I actually have (online) friends. */
            client.send(msgGroupToUser(myRef, userContext, friends));
        }
    }

    /**
     * Obtain the number of online presences this user currently has.
     *
     * @return the count of presences for this user.
     */
    int presenceCount() {
        int count = 0;
        if (myPresences != null) {
            for (String context : myPresences) {
                if (context != null) {
                    ++count;
                }
            }
        }
        return count;
    }

    /**
     * Obtain the reference string of the user whose presence this is.
     *
     * @return this presence's user's reference string.
     */
    String ref() {
        return myRef;
    }

    /**
     * Remove an existing presence for this user from the collection we are
     * following.
     *
     * @param context  The context the user was in
     * @param master  The presence server master instance.
     *
     * @return true if the presence was successfully removed, false if not
     */
    boolean removePresence(String context, PresenceServer master) {
        if (myPresences != null) {
            for (int i = 0; i < myPresences.length; ++i) {
                if (context.equals(myPresences[i])) {
                    myPresences[i] = null;
                    notifyFriendsAboutMe(false, context, master);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Set this user's friends set for some domain, i.e., the collection of
     * other users in the domain's social graph.  Note that this should only
     * ever be called once per user per domain, and will have the side effect
     * of performing any notifications that were pending the loading of the
     * social graph.
     *
     * @param friends  This user's social graph connections, represented as
     *    an object that encapsulates the domain's social graph's
     *    implementation's representation of a single user's connections.
     * @param domain  Social graph domain these connections belong to.
     * @param master  The presence server master instance.
     */
    public void userGraphIsReady(Iterable<String> friends, Domain domain,
                                 PresenceServer master)
    {
        if (myFriendsByDomain == null) {
            int count = Domain.maxIndex();
            myFriendsByDomain = new ArrayList<Iterable<String>>(count);
            for (int i = 0; i < count; ++i) {
                myFriendsByDomain.add(null);
            }
        }
        if (myFriendsByDomain.get(domain.index()) != null) {
            throw new RuntimeException("duplicate setFriends call for user " +
                                       myRef + ", domain " + domain.name());
        }
        myFriendsByDomain.set(domain.index(), friends);
        ++myDomainLoadCount;
        if (graphsAreReady() && myPresences != null) {
            for (String context : myPresences) {
                notifyFriendsAboutMe(true, context, master);
                notifyMeAboutFriends(context, master);
            }
        }
    }

    /**
     * Generate a notification message to a client, telling it about the
     * status of a user's friends.
     *
     * @param user  The ref of the user whose friends are of interest
     * @param context  The context in which the user presence is being notified
     * @param friends  A list of the currently online members of the user's
     *    social graph.
     */
    JSONLiteral msgGroupToUser(String user, String context,
                               Map<Domain, List<FriendInfo>> friends)
    {
        JSONLiteral msg = new JSONLiteral("presence", "gtou");
        msg.addParameter("touser", user);
        msg.addParameter("ctx", context);
        JSONLiteralArray group = new JSONLiteralArray();
        for (Map.Entry<Domain, List<FriendInfo>> entry : friends.entrySet()) {
            JSONLiteral domainInfo = new JSONLiteral();
            domainInfo.addParameter("domain", entry.getKey().name());
            domainInfo.addParameter("friends", entry.getValue());
            domainInfo.finish();
            group.addElement(domainInfo);
        }
        group.finish();
        msg.addParameter("group", group);
        msg.finish();
        return msg;
    }

    /**
     * Generate a notification message to a client, telling it to inform a
     * group of users about the change in presence status of a user.
     *
     * @param user  The ref of the user whose presence changed
     * @param userMeta  Optional user metadata.
     * @param context  The context the user is or was in
     * @param on  true if the user came online, false if they went offline
     * @param friends  A collection of lists of the refs of the users who
     *    should be informed, by domain and context
     * @param master  The presence server master instance.
     */
    JSONLiteral msgUserToGroup(String user, JSONObject userMeta,
                               String context, boolean on,
                               Map<Domain, Map<String, List<String>>> friends,
                               PresenceServer master)
    {
        JSONLiteral msg = new JSONLiteral("presence", "utog");
        msg.addParameter("user", user);
        msg.addParameterOpt("umeta", userMeta);
        msg.addParameter("ctx", context);
        msg.addParameterOpt("cmeta", master.getContextMetadata(context));
        msg.addParameter("on", on);
        JSONLiteralArray group = new JSONLiteralArray();
        for (Map.Entry<Domain, Map<String, List<String>>> entry : friends.entrySet()) {
            Domain domain = entry.getKey();
            Map<String, List<String>> who = entry.getValue();
            JSONLiteral obj = new JSONLiteral();
            obj.addParameter("domain", domain.name());
            JSONLiteralArray whoArr = new JSONLiteralArray();
            for (Map.Entry<String, List<String>> ctxUsers : who.entrySet()) {
                JSONLiteral ctxInfo = new JSONLiteral();
                ctxInfo.addParameter("ctx", ctxUsers.getKey());
                ctxInfo.addParameter("users", ctxUsers.getValue());
                ctxInfo.finish();
                whoArr.addElement(ctxInfo);
            }
            whoArr.finish();
            obj.addParameter("who", whoArr);
            obj.finish();
            group.addElement(obj);
        }
        group.finish();
        msg.addParameter("togroup", group);
        msg.finish();
        return msg;
    }

    /**
     * Simple encodable struct class holding the presence information
     * describing an online member of a user's social graph: a pair consisting
     * of the friend's user ref and the context ref of the context they are in.
     */
    private class FriendInfo implements Encodable {
        private String myUser;
        private JSONObject myUserMeta;
        private String myContext;
        private JSONObject myContextMeta;
        FriendInfo(String user, JSONObject userMeta, String context,
                   JSONObject contextMeta)
        {
            myUser = user;
            myUserMeta = userMeta;
            myContext = context;
            myContextMeta = contextMeta;
        }
        public JSONLiteral encode(EncodeControl control) {
            JSONLiteral result = new JSONLiteral(control);
            result.addParameter("user", myUser);
            result.addParameterOpt("umeta", myUserMeta);
            result.addParameter("ctx", myContext);
            result.addParameterOpt("cmeta", myContextMeta);
            result.finish();
            return result;
        }
    }
}
