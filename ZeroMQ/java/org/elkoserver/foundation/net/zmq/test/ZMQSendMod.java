package org.elkoserver.foundation.net.zmq.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.zmq.ZMQOutbound;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.util.trace.Trace;

/**
 * Context mod to test ZMQ outbound connections
 */
public class ZMQSendMod
    extends Mod
    implements ContextMod, ObjectCompletionWatcher
{
    /** Name of static object that will deliver outbound ZMQ messages */
    private String myOutboundName;

    /** The connection to send outbound messages on */
    private Connection myConnection;

    /** Trace object for logging */
    private Trace tr;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod("outbound")
    public ZMQSendMod(String outboundName) {
        myOutboundName = outboundName;
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this mod.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (!control.toClient()) {
            JSONLiteral result = new JSONLiteral("zmqsendmod", control);
            result.addParameter("outbound", myOutboundName);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Handle the 'log' verb.  Logs an arbitrary string to the ZMQ connection
     *
     * @param str  String to log
     */
    @JSONMethod("str")
    public void log(User from, String str) throws MessageHandlerException {
        ensureInContext(from);
        JSONLiteral msg = new JSONLiteral("logger", "log");
        msg.addParameter("str", str);
        msg.finish();
        if (myConnection != null) {
            myConnection.sendMsg(msg);
        } else {
            tr.errorm("uninitialized outbound connection");
        }
    }

    public void objectIsComplete() {
        Contextor contextor =  object().contextor();
        tr = contextor.appTrace();
        ZMQOutbound outbound =
            (ZMQOutbound) contextor.getStaticObject(myOutboundName);
        myConnection = outbound.getConnection();
    }
}
