package org.elkoserver.server.presence;

import java.util.Arrays;
import java.util.Iterator;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArrayIterator;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

class SimpleSocialGraph implements SocialGraph {
    /** Database that social graph is stored in. */
    private ObjDB myODB;

    /** The presence server lording over us. */
    private PresenceServer myMaster;

    /** The domain this graph describes. */
    private Domain myDomain;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Prefix string for looking up user graph records in the database. */
    private String myPrefix;

    SimpleSocialGraph() {
    }

    public void init(PresenceServer master, Domain domain, JSONObject conf) {
        myODB = master.objDB();
        myODB.addClass("ugraf", UserGraphDesc.class);
        myMaster = master;
        myDomain = domain;
        tr = master.appTrace();
        try {
            myPrefix = conf.optString("prefix", "g");
        } catch (JSONDecodingException e) {
            myPrefix = "g";
        }
        tr.worldi("init SimpleSocialGraph for domain '" + domain.name() +
                  "', odb prefix '" + myPrefix + "-'");
    }

    /**
     * Obtain the domain that this social graph describes.
     *
     * @return this social graph's domain.
     */
    public Domain domain() {
        return myDomain;
    }

    /**
     * Fetch the social graph for a new user presence from the object store.
     *
     * @param user  The user whose social graph should be fetched.
     */
    public void loadUserGraph(final ActiveUser user) {
        myODB.getObject(myPrefix + "-" + user.ref(), null, new ArgRunnable() {
            public void run(Object obj) {
                if (obj != null) {
                    final UserGraphDesc desc = (UserGraphDesc) obj;
                    Iterable<String> friends = new Iterable<String>() {
                       public Iterator<String> iterator() {
                           return new ArrayIterator<String>(desc.friends);
                       }
                    };
                    user.userGraphIsReady(friends, myDomain, myMaster);
                } else {
                    user.userGraphIsReady(null, myDomain, myMaster);
                    tr.warningi("no social graph info for user " +
                                user.ref() + " in domain " + myDomain.name());
                }
            }
        });
    }

    public void shutdown() {
    }

    public void update(PresenceServer master, Domain domain, JSONObject conf) {
    }
}
