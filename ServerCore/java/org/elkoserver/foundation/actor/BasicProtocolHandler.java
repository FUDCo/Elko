package org.elkoserver.foundation.actor;

import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.trace.Trace;

/**
 * Utility message handler implementation base class that supports a basic JSON
 * protocol for connection housekeeping.  The supported protocol is common to
 * many actors in Elko.  It includes the messages 'auth', 'debug',
 * 'disconnect', 'ping', and 'pong'.  This base class provides default
 * implementations for these messages that should be satisfactory in nearly all
 * circumstances.
 */
abstract public class BasicProtocolHandler
    implements Referenceable, DispatchTarget
{
    /**
     * Constructor.
     */
    protected BasicProtocolHandler() {
    }

    /**
     * JSON method for the 'auth' message.
     *
     * This message requests the server to authenticate the sender, according
     * to the type of user they want to become.  There is no reply, but if
     * authentication fails, the sender is disconnected.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"auth", auth:<i>AUTHDESC</i>,
     *                     label:<i>optSTR</i> } </tt><br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param auth  Authorization information being offered.
     * @param label  Descriptive label for this connection, for logging.
     */
    @JSONMethod({ "?auth", "label" })
    public void auth(BasicProtocolActor from, AuthDesc auth, OptString label) {
        if (!from.doAuth(this, auth, label.value("<anonymous>"))) {
            from.doDisconnect();
        }
    }

    /**
     * JSON method for the 'debug' message.
     *
     * This message delivers textual debugging information from the other end
     * of the connection.  The received text is written to the server log.<p>
     *
     * <u>recv</u>: <tt> { to:<i>ignored</i>, op:"debug",
     *                     msg:<i>STR</i> } </tt><br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param msg  Text to write to the server log;
     */
    @JSONMethod({ "msg" })
    public void debug(BasicProtocolActor from, String msg) {
        Trace.comm.eventi("Debug msg: " + msg);
    }

    /**
     * JSON method for the 'disconnect' message.
     *
     * This message requests the server to close its connection to the
     * sender.<p>
     *
     * <u>recv</u>: <tt> { to:<i>ignored</i>, op:"disconnect" } </tt><br>
     *
     * <u>send</u>: there is no reply, since the connection is closed
     *
     * @param from  The connection over which the message was received.
     */
    @JSONMethod
    public void disconnect(BasicProtocolActor from) {
        from.doDisconnect();
    }

    /**
     * JSON method for the 'ping' message.
     *
     * This message is a simple connectivity test.  Responds by sending a
     * 'pong' message back to the sender.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"ping",
     *                     tag:<i>optSTR</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>asReceived</i>, op:"pong",
     *                     tag:<i>asReceived</i> } </tt>
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string; if provided, it will be included in the
     *    reply.
     */
    @JSONMethod({ "tag" })
    public void ping(BasicProtocolActor from, OptString tag) {
        from.send(msgPong(this, tag.value(null)));
    }

    /**
     * JSON method for the 'pong' message.
     *
     * This message is the reply to an earlier 'ping' message.  It is simply
     * discarded.<p>
     *
     * <u>recv</u>: <tt> { to:<i>ignored</i>, op:"pong",
     *                     tag:<i>optSTR</i> } </tt><br>
     *
     * <u>send</u>: no reply is sent
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string, which should echo the tag (if any) from
     *    the 'ping' message that caused this 'pong' to be sent.
     */
    @JSONMethod({ "tag" })
    public void pong(BasicProtocolActor from, OptString tag) {
        /* Nothing to do here. */
    }

    /**
     * Generate a 'pong' message.
     *
     * @param target  Object the message is being sent to.
     * @param tag  Tag string (nominally from the 'ping' message that
     *    triggered this) or null.
     */
    static JSONLiteral msgPong(Referenceable target, String tag) {
        JSONLiteral msg = new JSONLiteral(target, "pong");
        msg.addParameterOpt("tag", tag);
        msg.finish();
        return msg;
    }
}
