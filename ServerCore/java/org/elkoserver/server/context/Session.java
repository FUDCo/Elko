package org.elkoserver.server.context;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;
import org.elkoserver.util.trace.TraceController;

/**
 * Singleton administrative object for entering and exiting contexts.
 *
 * <p>Unlike most objects, JSON messages may be sent to this object when the
 * user is in either the entered or exited state.  The behavior is somewhat
 * different in the two cases.  For example, when a user has not yet entered a
 * context, they can enter but not exit, and vice versa.  These states are
 * distinguished by whether the message seems to be arriving from a UserActor
 * (the pre-entry state) or from a User (the post-entry state).
 */
class Session extends BasicProtocolHandler {
    /** The contextor for this session. */
    private Contextor myContextor;

    /** Server object. */
    Server myServer;

    /** Trace object for handling the 'log' verb. */
    private Trace myLogger;

    /**
     * Construct.
     *
     * @param contextor  The contextor for this session.
     */
    Session(Contextor contextor, Server server) {
        myContextor = contextor;
        myLogger = Trace.trace("clientlogger");
        TraceController.setProperty("trace_clientlogger", "EVENT");
        myServer = server;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'session'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "session";
    }

    /**
     * Handle the 'log' verb.
     *
     * Write the text given to the log.
     *
     * @param text  The text to log.
     */
    @JSONMethod({ "text" })
    public void log(User from, String text) {
        myLogger.eventi(text);
    }

    /**
     * Handle the 'dump' verb
     *
     * Return various blobs of information about the state of this context
     * server.
     *
     * @param what  Indicator of which type of information is desired
     * @param context  Optional context parameter, when relevant
     * @param password  Password to verify that sender is allowed to do this
     */
    @JSONMethod({ "what", "password", "context" })
    public void dump(Deliverer from, String what, OptString testPassword,
                     OptString optContext) {
        String password =
           myServer.props().getProperty("conf.context.shutdownpassword", null);
        String contextRef = optContext.value(null);
        if (password == null || password.equals(testPassword.value(null))) {
            JSONLiteral reply = new JSONLiteral("session", "dump");
            reply.addParameter("what", what);
            if (what.equals("contexts")) {
                JSONLiteralArray list = new JSONLiteralArray();
                for (Context ctx : myContextor.contexts()) {
                    list.addElement(ctx.ref());
                }
                list.finish();
                reply.addParameter("contexts", list);
            } else if (what.equals("users")) {
                JSONLiteralArray list = new JSONLiteralArray();
                for (User user : myContextor.users()) {
                    if (contextRef == null ||
                            user.context().ref().equals(contextRef)) {
                        list.addElement(user.ref());
                    }
                }
                list.finish();
                reply.addParameter("users", list);
            } else if (what.equals("items")) {
                // dump items in 'context' or all items
            } else {
                reply.addParameter("error", "unknown 'what' value: " + what);
            }
            reply.finish();
            from.send(reply);
        }
    }


    /**
     * Handle the 'entercontext' verb.
     *
     * Enter the user who sent it into the context they asked for.
     *
     * @param user  The ID of the user who is entering.
     * @param name  The alleged name of the user who is entering.
     * @param context  Reference to the context they wish to enter.
     * @param contextTemplate  Optional reference to the template context from
     *    which the context should be derived.
     * @param sess  Client session ID for the connection to the context.
     * @param auth  Authorization code for a reserved entry.
     * @param utag  Factory tag string for synthetic user
     * @param uparam  Arbitrary object parameterizing synthetic user
     * @param debug This session will use debug settings, if enabled.
     * @param scope  Application scope for filtering mods
     */
    @JSONMethod({ "user", "name", "context", "ctmpl", "sess", "auth", "utag",
                  "?uparam", "debug", "scope" })
    public void entercontext(Deliverer from, OptString user, OptString name,
                             String context, OptString contextTemplate,
                             OptString sess, OptString auth, OptString utag,
                             JSONObject uparam, OptBoolean debug,
                             OptString scope)
        throws MessageHandlerException
    {
        if (from instanceof User) {
            throw new MessageHandlerException("already in a context");
        } else /* if (from instanceof UserActor) */ {
            UserActor fromActor = (UserActor) from;
            fromActor.enterContext(user.value(null), name.value(null), context,
                                   contextTemplate.value(null),
                                   sess.value(null), auth.value(null),
                                   utag.value(null), uparam,
                                   debug.value(false), scope.value(null));
        }
    }

    /**
     * Handle the 'exit' verb.
     *
     * Exit the context and disconnect the user who sent it -- except that the
     * user isn't in any context, so there's nothing to do.
     */
    @JSONMethod
    public void exit(Deliverer from) throws MessageHandlerException {
        throw new MessageHandlerException("not in a context");
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Shutdown the server.
     *
     * @param password  Password to verify that sender is allowed to do this.
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({ "password", "kill" })
    public void shutdown(Deliverer from, OptString testPassword,
                         OptBoolean kill)
        throws MessageHandlerException
    {
        User fromUser = null;
        if (from instanceof User) {
            fromUser = (User) from;
        }
        String password =
            myServer.props().getProperty("conf.context.shutdownpassword", null);
        if (password == null || password.equals(testPassword.value(null))) {
            myContextor.shutdownServer(kill.value(false));
            if (fromUser != null) {
                fromUser.exitContext("server shutting down", "shutdown",
                                     false);
            } else {
                from.send(Msg.msgExit(this, "server shutting down", "shutdown",
                                      false));
                ((UserActor) from).close();
            }
        } else {
            if (fromUser != null) {
                fromUser.exitContext(null, null, false);
            } else {
                ((UserActor) from).close();
            }
        }
    }
}
