package org.elkoserver.server.context.test;

import java.util.LinkedList;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.server.ServiceActor;
import org.elkoserver.foundation.server.ServiceLink;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.AdminObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.util.ArgRunnable;

/**
 * Internal object that acts as a client for the external 'echo' service.
 */
public class EchoClient extends AdminObject implements ArgRunnable {
    /** Connection to the workshop running the echo service. */
    private ServiceLink myServiceLink;

    /** Tag string indicating the current state of the service connection. */
    private String myStatus;

    /** Ordered list of handlers for pending requests to the service. */
    private LinkedList<ArgRunnable> myResultHandlers;

    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public EchoClient() {
        myStatus = "startup";
        myResultHandlers = new LinkedList<ArgRunnable>();
    }

    /**
     * Make this object live inside the context server.  In this case we
     * initiate a connection to the external echo service.
     *
     * @param ref  Reference string identifying this object in the static
     *    object table.
     * @param contextor  The contextor for this server.
     */
    public void activate(String ref, Contextor contextor) {
        super.activate(ref, contextor);
        myStatus = "connecting";
        contextor.findServiceLink("echo", this);
    }

    /**
     * Callback that is invoked when the service connection is established or
     * fails to be established.
     *
     * @param obj  The connection to the echo service, or null if connection
     *    setup failed.
     */
    public void run(Object obj) {
        if (obj != null) {
            myServiceLink = (ServiceLink) obj;
            myStatus = "connected";
        } else {
            myStatus = "failed";
        }
    }

    /**
     * Issue an 'echo' request to the external service.
     *
     * @param text  The text to be echoed.
     * @param resultHandler  Handler to be invoked with the echoed string,
     *     when it is received, or an error message string if there was a
     *     problem.
     */
    void probe(String text, ArgRunnable resultHandler) {
        if (myServiceLink != null) {
            myResultHandlers.addLast(resultHandler);
            JSONLiteral msg = new JSONLiteral("echotest", "echo");
            msg.addParameter("rep", this);
            msg.addParameter("text", text);
            msg.finish();
            myServiceLink.send(msg);
        } else {
            resultHandler.run("no connection to echo service");
        }
    }

    /**
     * Get the current status of the connection to the external service.
     *
     * @return a tag string describing the current connection state.
     */
    String status() {
        return myStatus;
    }

    /**
     * Handler for the 'echo' message, which is a reply to earlier an echo
     * requests sent to the external service.
     */
    @JSONMethod({ "text" })
    public void echo(ServiceActor from, String text)
        throws MessageHandlerException
    {
        ArgRunnable resultHandler = myResultHandlers.removeFirst();
        resultHandler.run(text);
    }
}
