package org.elkoserver.foundation.actor;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * An {@link Actor} that receives untargeted JSON messages over its connection.
 *
 * <p>This class is abstract, in that its implementation of the {@link
 * org.elkoserver.foundation.net.MessageHandler MessageHandler} interface is
 * incomplete: it implements the {@link
 * org.elkoserver.foundation.net.MessageHandler#processMessage processMessage()}
 * method, but subclasses must implement the {@link
 * org.elkoserver.foundation.net.MessageHandler#connectionDied connectionDied()}
 * method (as well as any {@link JSONMethod} methods for whatever specific
 * object behavior the subclass is intended for).
 *
 * <p>In contrast to {@link RoutingActor}, objects of this class disregard the
 * message targets of messages received, blindly assuming instead that all
 * messages received are for them.  This avoids setting up a lot of expensive
 * mechanism for a common special case, wherein a connection has a simple,
 * static message protocol with no object addressing.
 *
 * <p>This class provides default implementations for the 'ping', 'pong', and
 * 'debug' messages, since objects of the variety that would use this class
 * should always support those particular messages anyway.
 */
abstract public class NonRoutingActor
    extends Actor
    implements Referenceable, DispatchTarget
{
    /** Dispatcher for delivering messages based on the 'op' parameter. */
    private MessageDispatcher myDispatcher;

    /**
     * Constructor.
     *
     * @param connection  Connection associated with this actor.
     * @param dispatcher  Dispatcher to invoke message handlers based on 'op'.
     */
    public NonRoutingActor(Connection connection, MessageDispatcher dispatcher)
    {
        super(connection);
        myDispatcher = dispatcher;
    }

    /**
     * Send a 'debug' message over the connection, addressed to the 'error'
     * object.
     *
     * @param errorText  Error message text to send in the parameter 'msg'.
     */
    private void debugMsg(String errorText) {
        JSONLiteral msg = new JSONLiteral(this, "debug");
        msg.addParameter("msg", errorText);
        msg.finish();
        send(msg);
    }

    /**
     * Process a received message by dispatching to this object directly using
     * the dispatcher that was provided in this actor's constructor.
     *
     * @param connection  Connection over which the message was received.
     * @param rawMessage  The message received.  Normally this should be a
     *    {@link JSONObject}, but it could be a {@link Throwable} indicating a
     *    problem receiving or parsing the message.
     */
    public void processMessage(Connection connection, Object rawMessage) {
        Throwable report = null;
        if (rawMessage instanceof JSONObject) {
            JSONObject message = (JSONObject) rawMessage;

            try {
                myDispatcher.dispatchMessage(this, this, message);
            } catch (MessageHandlerException result) {
                report = result.getCause();
                if (report == null) {
                    report = result;
                } else {
                    Trace.comm.errorReportException(report,
                        "exception in message handler");
                }
            }
        } else if (rawMessage instanceof Throwable) {
            report = (Throwable) rawMessage;
        }
        if (report != null) {
            String warning = "message handler error: " + report;
            Trace.comm.warningm(warning);
            if (NetworkManager.TheDebugReplyFlag) {
                debugMsg(warning);
            }
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
    public void debug(Deliverer from, String msg) {
        Trace.comm.eventi("Debug msg: " + msg);
    }

    /**
     * JSON method for the 'ping' message.
     *
     * This message is a simple connectivity test.  Respond by sending a 'pong'
     * message back to the sender.<p>
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
    public void ping(Deliverer from, OptString tag) {
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
     * <u>send</u>: there is no reply sent
     *
     * @param from  The connection over which the message was received.
     * @param tag  Optional tag string, which should echo that (if any) from
     *    the 'ping' message that caused this 'pong' to be sent.
     */
    @JSONMethod({ "tag" })
    public void pong(Deliverer from, OptString tag) {
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
