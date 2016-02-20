package org.elkoserver.server.presence;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Referenceable;

/**
 * Singleton handler for the presence server 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 *   'reinit' - Requests the presence server to reinitialize itself.
 *
 *   'shutdown' - Requests the presence server to shut down, with an option to
 *      force abrupt termination.
 *
 *   'dump' - Request a dump of some or all of the presence server's state.
 *
 */
class AdminHandler extends BasicProtocolHandler {
    /** The presence server proper */
    private PresenceServer myPresenceServer;

    /**
     * Constructor.
     *
     * @param presenceServer  The presence server administered by this handler
     */
    AdminHandler(PresenceServer presenceServer) {
        myPresenceServer = presenceServer;
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
     * Handle the 'dump' verb.
     *
     * Request a dump of the presence server's state.
     *
     * @param from  The administrator asking for the information.
     * @param depth  Depth limit for the dump: 0 ==> counts only, 1 ==> adds
     *    user names, 2 ==> adds presence info, 3 ==> adds social graph data
     * @param user  A user to limit the dump to
     */
    @JSONMethod({ "depth", "user" })
    public void dump(PresenceActor from, int depth, OptString optUser)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String userName = optUser.value(null);

        int numUsers = 0;
        int numPresences = 0;
        JSONLiteralArray userDump = new JSONLiteralArray();
        for (ActiveUser user : myPresenceServer.users()) {
            if (userName == null || user.ref().equals(userName)) {
                ++numUsers;
                numPresences += user.presenceCount();
                if (depth > 0) {
                    JSONLiteral elem = new JSONLiteral();
                    elem.addParameter("user", user.ref());
                    if (depth > 1) {
                        elem.addParameter("pres", user.presences());
                    }
                    if (depth > 2) {
                        elem.addParameter("conn", user.encodeFriendsDump());
                    }
                    elem.finish();
                    userDump.addElement(elem);
                }
            }
        }
        userDump.finish();
        from.send(msgDump(this, numUsers, numPresences,
                          depth > 0 ? userDump : null));
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the presence server be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    public void reinit(PresenceActor from) throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        myPresenceServer.reinitServer();
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the presence server be shut down.
     *
     * @param from  The administrator sending the message.
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({ "kill" })
    public void shutdown(PresenceActor from, OptBoolean kill)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        myPresenceServer.shutdownServer(kill.value(false));
    }

    /**
     * Handle the 'update' verb.
     *
     * Update the state of a domain's social graph object.
     *
     * @param from  The client issuing the update.
     * @param domain  The domain being updated.
     * @param conf  Domain-specific configuration update parameters.
     */
    @JSONMethod({ "domain", "conf" })
    public void update(PresenceActor from, String domain, JSONObject conf)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        myPresenceServer.updateDomain(domain, conf, from);
    }

    /**
     * Generate a 'dump' message.
     */
    static JSONLiteral msgDump(Referenceable target, int numUsers,
                               int numPresences, JSONLiteralArray userDump)
    {
        JSONLiteral msg = new JSONLiteral(target, "dump");
        msg.addParameter("numusers", numUsers);
        msg.addParameter("numpresences", numPresences);
        if (userDump != null) {
            msg.addParameter("users", userDump);
        }
        msg.finish();
        return msg;
    }
}
