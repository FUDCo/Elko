package org.elkoserver.server.presence;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.ExcludingIterator;
import org.elkoserver.util.FilteringIterator;
import org.elkoserver.util.trace.Trace;

/**
 * Social graph based on the notion that everybody is connected to everybody
 * else.  So we don't need to actually store anything, but can dynamically
 * iterate a user's friends: a user's friends who are online at any given
 * moment are all users who are online.
 *
 * In order to throttle the massive numbers of presence updates that will be
 * generated when lots of users are online at once, this graph also supports a
 * stochastic filter that randomly excludes users according to a tunable
 * parameter.
 */
class UniversalGraph implements SocialGraph {
    /** The presence server lording over us. */
    private PresenceServer myMaster;

    /** The domain this graph describes. */
    private Domain myDomain;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Number of pseudo-friends someone has, for throttling.  A negative
        value is unthrottled. */
    private int myPseudoFriendCount;

    /** Random number generator, for random friendship. */
    static private SecureRandom theRandom = new SecureRandom();

    UniversalGraph() {
    }

    public void init(PresenceServer master, Domain domain, JSONObject conf) {
        myMaster = master;
        myDomain = domain;
        tr = master.appTrace();
        try {
            myPseudoFriendCount = conf.optInt("friends", -1);
        } catch (JSONDecodingException e) {
            myPseudoFriendCount = -1;
        }
        tr.worldi("init UniversalGraph for domain '" + domain.name() + "'");
    }

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    public Domain domain() {
        return myDomain;
    }

    private static class UserFilter
        implements FilteringIterator.Filter<ActiveUser, String>
    {
        public String transform(ActiveUser from) {
            return from.ref();
        }
    }

    private class PseudoFriendFilter<V> extends ExcludingIterator<V> {
        private V myExclusion;
        private float myStochasticFriendshipOdds;

        public PseudoFriendFilter(Iterator<V> base, V exclusion) {
            super(base);
            myExclusion = exclusion;
            if (myPseudoFriendCount < 0) {
                myStochasticFriendshipOdds = 1.0f;
            } else {
                int userCount = myMaster.userCount();
                if (userCount < myPseudoFriendCount) {
                    myStochasticFriendshipOdds = 1.0f;
                } else {
                    myStochasticFriendshipOdds =
                        (float) myPseudoFriendCount / (float) userCount;
                }
            }
        }

        public boolean isExcluded(V element) {
            if (element.equals(myExclusion)) {
                return true;
            } else if (myStochasticFriendshipOdds >= 1.0f) {
                return false;
            } else {
                return theRandom.nextFloat() < myStochasticFriendshipOdds;
            }
        }
    }

    /**
     * Fetch the social graph for a new user presence from the object store.
     *
     * @param user  The user whose social graph should be fetched.
     */
    public void loadUserGraph(final ActiveUser user) {
        Iterable<String> friends = new Iterable<String>() {
            public Iterator<String> iterator() {
                return new PseudoFriendFilter<String>(
                    new FilteringIterator<ActiveUser, String>(
                        myMaster.users().iterator(),
                        new UserFilter()),
                    user.ref());
            }
        };
        user.userGraphIsReady(friends, myDomain, myMaster);
    }

    public void shutdown() {
    }

    public void update(PresenceServer master, Domain domain, JSONObject conf) {
    }
}
