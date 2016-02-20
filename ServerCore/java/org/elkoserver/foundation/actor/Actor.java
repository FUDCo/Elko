package org.elkoserver.foundation.actor;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;

/**
 * An object representing some entity interacting with this server (whatever
 * server this is) over the net.  It sits on top of a Connection.  It is both a
 * Deliverer that delivers outgoing messages over the connection and a message
 * acceptor that receives and dispatches incoming messages arriving on the
 * connection.
 */
abstract public class Actor implements Deliverer, MessageHandler
{
    /** Connection to communicate with the entity at the other end. */
    private Connection myConnection;

    /**
     * Construct a new Actor.
     *
     * @param connection  Connection to communicate with the entity at the
     *    other end.
     */
    public Actor(Connection connection) {
        myConnection = connection;
    }

    /**
     * Close this Actor's connection.
     */
    public void close() {
        myConnection.close();
    }

    /**
     * Send a message over this Actor's connection to the entity at the other
     * end.
     *
     * @param message  The message to send.
     */
    public void send(JSONLiteral message) {
        myConnection.sendMsg(message);
    }

    /**
     * Create an 'auth' message.
     *
     * @param target  Object the message is being sent to.
     * @param auth  Authentication information to use.
     * @param label  Label to identify the entity seeking authorization.
     */
    static public JSONLiteral msgAuth(Referenceable target, AuthDesc auth,
                                      String label)
    {
        return msgAuth(target.ref(), auth, label);
    }

    /**
     * Create an 'auth' message.
     *
     * @param target  Object the message is being sent to.
     * @param auth  Authentication information to use.
     * @param label  Label to identify the entity seeking authorization.
     */
    static public JSONLiteral msgAuth(String target, AuthDesc auth,
                                      String label)
    {
        JSONLiteral msg = new JSONLiteral(target, "auth");
        msg.addParameterOpt("auth", auth);
        msg.addParameterOpt("label", label);
        msg.finish();
        return msg;
    }
}
