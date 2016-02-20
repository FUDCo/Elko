package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.JSONByteIOFramerFactory;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NullMessageHandler;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.ContextShutdownWatcher;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.util.trace.Trace;

/**
 * Context mod to test ZMQ outbound connections
 */
public class ZMQSendTester
    extends Mod
    implements ContextMod, ObjectCompletionWatcher, ContextShutdownWatcher
{
    /** Address to connect to */
    private String myAddress;

    /** Outbound ZMQ connection to myAddress, or null if not yet open */
    private Connection myOutbound;

    /** Flag to interlock connection close and context shutdown */
    private boolean amDead;

    /** Trace object for logging */
    private Trace tr;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod("address")
    public ZMQSendTester(String address) {
        myAddress = address;
        amDead = false;
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
            JSONLiteral result = new JSONLiteral("zmqsendtest", control);
            result.addParameter("address", myAddress);
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
        if (myOutbound != null) {
            myOutbound.sendMsg(msg);
        } else {
            tr.errorm("received 'log' request before outbound connection ready");
        }
    }

    public void objectIsComplete() {
        context().registerContextShutdownWatcher(this);
        Contextor contextor =  object().contextor();
        tr = contextor.appTrace().subTrace("zmq");
        contextor.server().networkManager().connectVia(
            "org.elkoserver.foundation.net.zmq.ZeroMQConnectionManager",
            "", // XXX propRoot, needs to come from somewhere
            myAddress,
            new MessageHandlerFactory() {
                public MessageHandler provideMessageHandler(Connection conn) {
                    if (amDead) {
                        conn.close();
                    } else {
                        myOutbound = conn;
                    }
                    return new NullMessageHandler(tr);
                }
            },
            tr);
    }

    public void noteContextShutdown() {
        if (!amDead) {
            amDead = true;
            if (myOutbound != null) {
                myOutbound.close();
            }
        }
    }
}
