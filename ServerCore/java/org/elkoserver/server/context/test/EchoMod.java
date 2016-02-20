package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.ContextMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;
import org.elkoserver.util.ArgRunnable;

/**
 * Mod to enable a context user to exercise the external 'echo' service.
 */
public class EchoMod extends Mod
    implements ContextMod, ObjectCompletionWatcher
{
    /** The internal object that acts as the client for the echo service. */
    private EchoClient myService;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public EchoMod() {
        myService = null;
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
        JSONLiteral result = new JSONLiteral("echomod", control);
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'echo' message.  This invokes the external
     * echo service, if possible.
     */
    @JSONMethod({ "text" })
    public void echo(final User from, String text)
        throws MessageHandlerException
    {
        ensureSameContext(from);
        if (myService != null) {
            myService.probe(text, new ArgRunnable() {
                public void run(Object obj) {
                    JSONLiteral msg = new JSONLiteral(object(), "echo");
                    msg.addParameter("text", (String) obj);
                    msg.finish();
                    from.send(msg);
                }
            });
        } else {
            JSONLiteral msg = new JSONLiteral(object(), "echo");
            msg.addParameter("error", "no service");
            msg.finish();
            from.send(msg);
        }
    }

    /**
     * Message handler for the 'status' message.  This requests a report on
     * the state of the echo client connection.
     */
    @JSONMethod
    public void status(User from) throws MessageHandlerException {
        ensureSameContext(from);
        JSONLiteral msg = new JSONLiteral(object(), "status");
        String status;
        if (myService != null) {
            status = myService.status();
        } else {
            status = "no service";
        }
        msg.addParameter("status", status);
        msg.finish();
        from.send(msg);
    }

    /* ----- ObjectCompletionWatcher interface ----- */

    /**
     * Take notice that the associated context is complete.  In this case, we
     * lookup the echo client object from the environment, now that (since
     * the context is complete) there is an environment to look it up from.
     */
    public void objectIsComplete() {
        myService =
            (EchoClient) object().contextor().getStaticObject("echoclient");
    }
}
