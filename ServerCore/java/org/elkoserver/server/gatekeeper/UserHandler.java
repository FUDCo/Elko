package org.elkoserver.server.gatekeeper;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

/**
 * Singleton handler for the gatekeeper 'user' protocol.
 *
 * The 'user' protocol consists of the requests:
 *
 *   'reserve' - Requests a reservation on the user's behalf for entry into a
 *      particular context.
 *
 *   'setpassword' - Requests that the user's stored password be changed.
 */
class UserHandler extends BasicProtocolHandler {
    /** The gatekeeper proper. */
    private Gatekeeper myGatekeeper;

    /**
     * Constructor.
     *
     * @gatekeeper  The gatekeeper itself.
     */
    UserHandler(Gatekeeper gatekeeper) {
        myGatekeeper = gatekeeper;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'gatekeeper'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "gatekeeper";
    }

    /**
     * Handle the 'reserve' verb.
     *
     * Request a reservation to enter a context server.
     *
     * @param from  The user asking for the reservation.
     * @param protocol  The protocol it wants to use.
     * @param contextName  The context it is seeking.
     * @param id  The user who is asking for this.
     * @param name  Optional readable name for the user.
     * @param password  Password for entry, when relevant.
     */
    @JSONMethod({ "protocol", "context", "id", "name", "password" })
    public void reserve(final GatekeeperActor from, String protocol,
                        final String context, OptString id,
                        OptString name, OptString password)
    {
        final String idStr = id.value(null);

        myGatekeeper.authorizer().reserve(
            protocol, context, idStr, name.value(null), password.value(null),
            new ReservationResultHandler() {
                public void handleReservation(
                    String actor, String actualContext, String name,
                    String hostport, String auth)
                {
                    from.send(msgReserve(UserHandler.this, idStr,
                                         actualContext, actor, name, hostport,
                                         auth, null));
                }
                public void handleFailure(String failure) {
                    from.send(msgReserve(UserHandler.this, idStr, context,
                                         null, null, null, null, failure));
                }
            });
    }

    /**
     * Handle the 'setpassword' verb.
     *
     * Request to change a user's password.
     *
     * @param from  The connection asking for the password change.
     * @param id  The user who is asking for this.
     * @param oldpassword  Current password, to check for permission.
     * @param newpassword  New password setting.
     */
    @JSONMethod({ "id", "oldpassword", "newpassword" })
    public void setpassword(final GatekeeperActor from, final String id,
                            OptString oldpassword, OptString newpassword)
    {
        myGatekeeper.authorizer().setPassword(
            id,
            oldpassword.value(null),
            newpassword.value(null),
            new SetPasswordResultHandler() {
                public void handle(String failure) {
                    from.send(msgSetPassword(UserHandler.this, id, failure));
                }
            });
    }

    /**
     * Create a 'reserve' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param id  The ID for which the reservation was requested, or null if
     *    none.
     * @param context  Context the reservation is for.
     * @param actor  Actor the reservation is for, or null for anonymous.
     * @param hostport  Host:port to connect to, or null in error case.
     * @param auth  Authorization code for entry, or null in error case.
     * @param deny  Error message in error case, or null in normal case.
     */
    static JSONLiteral msgReserve(Referenceable target, String id,
        String context, String actor, String name, String hostPort,
        String auth, String deny)
    {
        JSONLiteral msg = new JSONLiteral(target, "reserve");
        msg.addParameterOpt("id", id);
        msg.addParameter("context", context);
        msg.addParameterOpt("actor", actor);
        msg.addParameterOpt("name", name);
        msg.addParameterOpt("hostport", hostPort);
        msg.addParameterOpt("auth", auth);
        msg.addParameterOpt("deny", deny);
        msg.finish();
        return msg;
    }

    /**
     * Create 'setpassword' reply message.
     *
     * @param target  Object the message is being sent to.
     * @param id  Actor whose password was requested to be changed.
     * @param failure  Error message, or null if no error.
     */
    static JSONLiteral msgSetPassword(Referenceable target, String id,
                                      String failure)
    {
        JSONLiteral msg = new JSONLiteral(target, "setpassword");
        msg.addParameter("id", id);
        msg.addParameterOpt("failure", failure);
        msg.finish();
        return msg;
    }
}
