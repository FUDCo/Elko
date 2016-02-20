package org.elkoserver.foundation.actor;

import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;

/**
 * An {@link Actor} that receives targeted JSON messages over its connection.
 *
 * <p>This class is abstract, in that its implementation of the {@link
 * org.elkoserver.foundation.net.MessageHandler MessageHandler} interface is
 * incomplete: it implements the {@link
 * org.elkoserver.foundation.net.MessageHandler#processMessage processMessage()}
 * method, but subclasses must implement {@link
 * org.elkoserver.foundation.net.MessageHandler#connectionDied connectionDied()}
 * method (as well as any {@link JSONMethod} methods for whatever specific
 * object behavior the subclass is intended for).
 */
abstract public class RoutingActor extends Actor implements DispatchTarget
{
    /** Decoder for object references and message dispatch. */
    private RefTable myRefTable;

    /**
     * Constructor.
     *
     * @param connection  Connection associated with this actor.
     * @param refTable  Table for object ref decoding and message dispatch.
     */
    public RoutingActor(Connection connection, RefTable refTable) {
        super(connection);
        myRefTable = refTable;
    }

    /**
     * Send a 'debug' message over the connection, addressed to the 'error'
     * object.
     *
     * @param errorText  Error message text to send in the parameter 'msg'.
     */
    private void debugMsg(String errorText) {
        JSONLiteral msg = new JSONLiteral("error", "debug");
        msg.addParameter("msg", errorText);
        msg.finish();
        send(msg);
    }

    /**
     * Process a received message by dispatching it to the object that the
     * message addresses as its target, according to the {@link RefTable} that
     * was provided in this actor's constructor.
     *
     * @param connection  Connection over which the message was received.
     * @param receivedMessage  The message received.  Normally this should be a
     *    {@link JSONObject}, but it could be a {@link Throwable} indicating a
     *    problem receiving or parsing the message.
     */
    public void processMessage(Connection connection, Object receivedMessage) {
        Throwable problem = null;
        if (receivedMessage instanceof JSONObject) {
            JSONObject message = (JSONObject) receivedMessage;

            try {
                myRefTable.dispatchMessage(this, message);
            } catch (MessageHandlerException result) {
                problem = result.getCause();
                if (problem == null) {
                    problem = result;
                } else {
                    Trace.comm.errorReportException(problem,
                        "exception in message handler");
                }
            }
        } else if (receivedMessage instanceof Throwable) {
            problem = (Throwable) receivedMessage;
        }
        if (problem != null) {
            String warning = "error handling message: " + problem;
            Trace.comm.warningm(warning);
            if (NetworkManager.TheDebugReplyFlag) {
                debugMsg(warning);
            }
        }
    }
}
