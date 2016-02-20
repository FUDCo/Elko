package org.elkoserver.server.director;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.HashSetMulti;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Singleton handler for the director 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 *   'close' - Requests the closure of a particular context or disconnection of
 *      a particular user.
 *
 *   'dump' - Requests a detailed description of the providers offering
 *      services via this director, the contexts they are currently serving,
 *      and the users currently in those contexts.  Optionally allows limiting
 *      the scope of the query to particular families of contexts or users,
 *      and limiting the level of detail in the information returned.
 *
 *   'find' - Requests the provider(s) serving a particular context or user.
 *
 *   'listcontexts' - Requests a list of currently active contexts.
 *
 *   'listproviders' - Requests a list of currently operational context
 *      providers.
 *
 *   'listusers' - Requests a list of currently connected users.
 *
 *   'reinit' - Requests the director to order the reinitialization of zero or
 *      more of the provider servers it knows about, and, optionally, itself.
 *
 *   'relay' - Requests the director to deliver an arbitrary message to a
 *      context, context family, or user, by relaying through the appropriate
 *      provider servers for the message target's current location.
 *
 *   'say' - Requests the director to deliver text in a 'say' message to a
 *      context, context family, or user, by relaying through the appropriate
 *      provider servers for the message target's current location.
 *
 *   'shutdown' - Requests the director to order the shut down of zero or more
 *     of the provider servers it knows about, and, optionally, itself.  Also
 *     has an option to force abrupt termination.
 *
 *   'unwatch' - Requests cancellation of an earlier 'watch' request.
 *
 *   'watch' - Requests the director to send notifications whenever a
 *      particular context or context family opens or closes and/or whenever
 *      a particular user arrives or departs.
 */
class AdminHandler extends BasicProtocolHandler {
    /** The director for this handler. */
    private Director myDirector;

    /**
     * Constructor.
     *
     * @param director  The Director object for this handler.
     */
    AdminHandler(Director director) {
        myDirector = director;
    }

    /**
     * Do the actual work of a 'find' or 'watch' verb.
     *
     * @param watch  true if doing a watch, false if just a find.
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    private void doFind(boolean watch, DirectorActor from, OptString context,
                        OptString user)
        throws MessageHandlerException
    {
        String contextName = context.value(null);
        String userName = user.value(null);
        if (userName != null && contextName != null) {
            throw new MessageHandlerException(
                "context and user parameters are mutually exclusive");
        } else if (userName == null && contextName == null) {
            throw
                new MessageHandlerException(
                    "context or user parameter missing");
        } else if (userName != null) {
            findUser(userName, from);
            if (watch) {
                from.admin().watchUser(userName);
            }
        } else /* if (contextName != null) */ {
            findContext(contextName, from);
            if (watch) {
                from.admin().watchContext(contextName);
            }
        }
    }

    /**
     * Locate a context and send out information about it.
     *
     * @param contextName  The name of the context of interest.
     * @param admin  Who to send the information to.
     */
    void findContext(String contextName, DirectorActor admin) {
        OpenContext context = myDirector.getContext(contextName);
        if (context != null) {
            admin.send(msgContext(this, contextName, true,
                                  context.provider().actor().label(), null));
        } else {
            HashSetMulti<OpenContext> clones =
                myDirector.contextClones(contextName);
            if (!clones.isEmpty()) {
                admin.send(msgContext(this, contextName, true, null,
                                      encodeContexts(clones)));
            } else {
                admin.send(msgContext(this, contextName, false, null, null));
            }
        }
    }

    /**
     * Locate a user and send out information about it.
     *
     * @param userName  The name of the user of interest.
     * @param admin  Who to send the information to.
     */
    void findUser(String userName, DirectorActor admin) {
        HashSetMulti<OpenContext> contexts =
            myDirector.userContexts(userName);
        if (contexts.isEmpty()) {
            contexts = myDirector.userCloneContexts(userName);
        }
        if (contexts.iterator().hasNext()) {
            admin.send(msgUser(this, userName, true,
                               encodeContexts(contexts)));
        } else {
            admin.send(msgUser(this, userName, false, null));
        }
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * know as 'admin'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "admin";
    }

    /**
     * Handle the 'close' verb.
     *
     * Request that a user or context be terminated.
     *
     * @param from  The administrator asking for the closure.
     * @param context  The context to be closed.
     * @param user  The user to be closed.
     */
    @JSONMethod({ "context", "user" })
    public void close(DirectorActor from, OptString context, OptString user)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();

        String contextName = context.value(null);
        String userName = user.value(null);
        JSONLiteral msg = msgClose(myDirector.providerHandler(), contextName,
                                   userName, false);
        myDirector.targetedBroadCast(null, contextName, userName, msg);
    }

    /**
     * Handle the 'dump' verb.
     *
     * Request a dump of the director's state.
     *
     * @param from  The administrator asking for the information.
     * @param depth  Depth limit for the dump.
     * @param provider  A provider to limit the dump to.
     * @param context  A context to limit the dump to.
     */
    @JSONMethod({ "depth", "provider", "context" })
    public void dump(DirectorActor from, int depth, OptString provider,
                     OptString context)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String providerName = provider.value(null);
        String contextName = context.value(null);

        int numProviders = 0;
        int numContexts = 0;
        int numUsers = 0;
        List<ProviderDump> providerList = new LinkedList<ProviderDump>();
        
        for (Provider subj : myDirector.providers()) {
            if (providerName == null || subj.matchLabel(providerName)) {
                ProviderDump providerDump =
                    new ProviderDump(depth, subj, contextName);
                if (providerDump.numContexts() > 0 || contextName == null) {
                    ++numProviders;
                    numContexts += providerDump.numContexts();
                    numUsers += providerDump.numUsers();
                    if (depth > 0) {
                        providerList.add(providerDump);
                    }
                }
            }
        }
        from.send(msgDump(this, numProviders, numContexts, numUsers,
                          providerList));
    }

    private class ProviderDump implements Encodable {
        private int myNumContexts;
        private int myNumUsers;
        private Provider myProvider;
        private List<ContextDump> myOpenContexts;

        ProviderDump(int depth, Provider provider, String contextName) {
            myNumContexts = 0;
            myNumUsers = 0;
            myProvider = provider;
            myOpenContexts = new LinkedList<ContextDump>();

            for (OpenContext context : provider.contexts()) {
                if (contextName == null || contextName.equals(context.name())){
                    ContextDump contextDump = new ContextDump(depth, context);
                    ++myNumContexts;
                    myNumUsers += context.userCount();
                    if (depth > 1) {
                        myOpenContexts.add(contextDump);
                    }
                }
            }
        }

        int numContexts() {
            return myNumContexts;
        }

        int numUsers() {
            return myNumUsers;
        }

        public JSONLiteral encode(EncodeControl control) {
            JSONLiteral literal = new JSONLiteral("providerdesc", control);
            literal.addParameter("provider", myProvider.actor().label());
            literal.addParameter("numcontexts", myNumContexts);
            literal.addParameter("numusers", myNumUsers);
            literal.addParameter("load", myProvider.loadFactor());
            literal.addParameter("capacity", myProvider.capacity());
            literal.addParameter("hostports",
                                 encodeStrings(myProvider.hostPorts()));
            literal.addParameter("protocols",
                                 encodeStrings(myProvider.protocols()));
            literal.addParameter("serving",
                                 encodeStrings(myProvider.services()));
            if (myOpenContexts.size() > 0) {
                literal.addParameter("contexts",
                                     encodeEncodableList(myOpenContexts));
            }
            literal.finish();
            return literal;
        }
    }

    private class ContextDump implements Encodable {
        private OpenContext myContext;
        private int myDepth;

        ContextDump(int depth, OpenContext context) {
            myDepth = depth;
            myContext = context;
        }

        public JSONLiteral encode(EncodeControl control) {
            JSONLiteral literal = new JSONLiteral("contextdesc", control);
            literal.addParameter("context", myContext.name());
            literal.addParameter("numusers", myContext.userCount());
            if (myDepth > 2) {
                literal.addParameter("users",
                                     encodeStrings(myContext.users()));
            }
            literal.finish();
            return literal;
        }
    }

    /**
     * Handle the 'find' verb.
     *
     * Request the location of a context or user.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    @JSONMethod({ "context", "user" })
    public void find(DirectorActor from, OptString context, OptString user)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        doFind(false, from, context, user);
    }

    /**
     * Handle the 'listcontexts' verb.
     *
     * Request a list of the contexts currently open.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    public void listcontexts(DirectorActor from)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        from.send(
            msgListcontexts(this, encodeContexts(myDirector.contexts())));
    }

    /**
     * Handle the 'listproviders' verb.
     *
     * Request a list of the providers currently serving.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    public void listproviders(DirectorActor from)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        from.send(
            msgListproviders(this, encodeProviders(myDirector.providers())));
    }

    /**
     * Handle the 'listusers' verb.
     *
     * Request a list of the users currently online.
     *
     * @param from  The administrator asking for the information.
     */
    @JSONMethod
    public void listusers(DirectorActor from) throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        from.send(msgListusers(this, encodeStrings(myDirector.users())));
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that one or more servers be reset.
     *
     * @param from  The administrator sending the message.
     * @param provider  The provider to be re-init'ed.
     * @param director  true if this director itself should be re-init'ed.
     */
    @JSONMethod({ "provider", "director" })
    public void reinit(DirectorActor from, OptString provider,
                       OptBoolean director)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String providerName = provider.value(null);
        if (providerName != null) {
            JSONLiteral msg = msgReinit(myDirector.providerHandler());
            for (Provider subj : myDirector.providers()) {
                if (providerName.equals("all") ||
                        subj.matchLabel(providerName)) {
                    subj.actor().send(msg);
                }
            }
        }
        if (director.value(false)) {
            myDirector.reinitServer();
        }
    }

    /**
     * Handle the 'relay' verb.
     *
     * Request that a message be relayed to a user or context.
     *
     * @param from  The administrator sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param msg  The message to relay to them.
     */
    @JSONMethod({ "context", "user", "msg" })
    public void relay(DirectorActor from, OptString context, OptString user,
                      JSONObject msg)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        myDirector.doRelay(null, context, user, msg);
    }

    /**
     * Handle the 'say' verb.
     *
     * Request that text be sent to a user or context.
     *
     * @param from  The administrator sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param text  The message to send them.
     */
    @JSONMethod({ "context", "user", "text" })
    public void say(DirectorActor from, OptString context, OptString user,
                    String text)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String contextName = context.value(null);
        String userName = user.value(null);
        JSONLiteral msg = msgSay(myDirector.providerHandler(), contextName,
                                 userName, text);
        myDirector.targetedBroadCast(null, contextName, userName, msg);
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that one or more servers be shut down.
     *
     * @param from  The administrator sending the message.
     * @param provider  The provider(s) to be shut down, if any.
     * @param director  true if this director itself should be shut down.
     * @param kill  True if provider(s) should be shutdown immediately instead
     *    of cleaning up.
     */
    @JSONMethod({ "provider", "director", "kill" })
    public void shutdown(DirectorActor from, OptString provider,
                         OptBoolean director, OptBoolean optKill)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String providerName = provider.value(null);
        boolean kill = optKill.value(false);
        if (providerName != null) {
            JSONLiteral msg = msgShutdown(myDirector.providerHandler(), kill);
            for (Provider subj : myDirector.providers()) {
                if (providerName.equals("all") ||
                        subj.matchLabel(providerName)) {
                    subj.actor().send(msg);
                }
            }
        }
        if (director.value(false)) {
            myDirector.shutdownServer(false);
        }
    }

    /**
     * Handle the 'unwatch' verb.
     *
     * Request a previously requested 'watch' be stopped.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context that was watched.
     * @param user  The user that was watched.
     */
    @JSONMethod({ "context", "user" })
    public void unwatch(DirectorActor from, OptString context, OptString user)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        String contextName = context.value(null);
        String userName = user.value(null);
        if (contextName != null && userName != null) {
            throw new MessageHandlerException(
                "context and user parameters are mutually exclusive");
        } else if (contextName == null && userName == null) {
            throw new MessageHandlerException(
                "context or user parameter missing");
        } else if (contextName != null) {
            from.admin().unwatchContext(contextName);
        } else /* if (userName != null) */ {
            from.admin().unwatchUser(userName);
        }
    }

    /**
     * Handle the 'watch' verb.
     *
     * Request notification about changes in the location of a context or
     * user.
     *
     * @param from  The administrator asking for the information.
     * @param context  The context sought.
     * @param user  The user sought.
     */
    @JSONMethod({ "context", "user" })
    public void watch(DirectorActor from, OptString context, OptString user)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        doFind(true, from, context, user);
    }

    /**
     * Generate a JSONLiteralArray of context names from a sequence of
     * OpenContext objects.
     */
    static JSONLiteralArray encodeContexts(Iterable<OpenContext> contexts) {
        JSONLiteralArray array = new JSONLiteralArray();
        for (OpenContext context : contexts) {
            array.addElement(context.name());
        }
        array.finish();
        return array;
    }

    /**
     * Generate a JSONLiteralArray from a linked list of Encodable objects.
     */
    static JSONLiteralArray encodeEncodableList(
        List<? extends Encodable> list)
    {
        JSONLiteralArray array = new JSONLiteralArray();
        for (Encodable elem : list) {
            array.addElement(elem);
        }
        array.finish();
        return array;
    }

    /**
     * Generate a JSONLiteralArray of provider labels from a set of provider
     * DirectorActor objects.
     */
    static JSONLiteralArray encodeProviders(Set<Provider> providers) {
        JSONLiteralArray array = new JSONLiteralArray();
        for (Provider subj : providers) {
            array.addElement(subj.actor().label());
        }
        array.finish();
        return array;
    }

    /**
     * Generate a JSONLiteralArray of strings from a collection of strings.
     */
    static JSONLiteralArray encodeStrings(Collection<String> strings) {
        JSONLiteralArray array = new JSONLiteralArray();
        for (String str : strings) {
            array.addElement(str);
        }
        array.finish();
        return array;
    }

    /**
     * Generate a 'close' message.
     */
    static JSONLiteral msgClose(Referenceable target, String contextName,
                                String userName, boolean isDup)
    {
        JSONLiteral msg = new JSONLiteral(target, "close");
        msg.addParameterOpt("context", contextName);
        msg.addParameterOpt("user", userName);
        if (isDup) {
            msg.addParameter("dup", isDup);
        }
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'context' message.
     */
    static JSONLiteral msgContext(Referenceable target, String contextName,
        boolean open, String provider, JSONLiteralArray clones)
    {
        JSONLiteral msg = new JSONLiteral(target, "context");
        msg.addParameter("context", contextName);
        msg.addParameter("open", open);
        msg.addParameterOpt("provider", provider);
        msg.addParameterOpt("clones", clones);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'dump' message.
     */
    static JSONLiteral msgDump(Referenceable target, int numProviders,
        int numContexts, int numUsers, List<ProviderDump> providerList)
    {
        JSONLiteral msg = new JSONLiteral(target, "dump");
        msg.addParameter("numproviders", numProviders);
        msg.addParameter("numcontexts", numContexts);
        msg.addParameter("numusers", numUsers);
        if (providerList.size() > 0) {
            msg.addParameter("providers", encodeEncodableList(providerList));
        }
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'listcontexts' message.
     */
    static JSONLiteral msgListcontexts(Referenceable target,
                                       JSONLiteralArray contexts)
    {
        JSONLiteral msg = new JSONLiteral(target, "listcontexts");
        msg.addParameter("contexts", contexts);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'listproviders' message.
     */
    static JSONLiteral msgListproviders(Referenceable target,
                                        JSONLiteralArray providers)
    {
        JSONLiteral msg = new JSONLiteral(target, "listproviders");
        msg.addParameter("providers", providers);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'listusers' message.
     */
    static JSONLiteral msgListusers(Referenceable target,
                                    JSONLiteralArray users)
    {
        JSONLiteral msg = new JSONLiteral(target, "listusers");
        msg.addParameter("users", users);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'reinit' message.
     */
    static JSONLiteral msgReinit(Referenceable target) {
        JSONLiteral msg = new JSONLiteral(target, "reinit");
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'say' message.
     */
    static JSONLiteral msgSay(Referenceable target, String contextName,
                              String userName, String text)
    {
        JSONLiteral msg = new JSONLiteral(target, "say");
        msg.addParameterOpt("context", contextName);
        msg.addParameterOpt("user", userName);
        msg.addParameter("text", text);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'shutdown' message.
     */
    static JSONLiteral msgShutdown(Referenceable target, boolean kill) {
        JSONLiteral msg = new JSONLiteral(target, "shutdown");
        if (kill) {
            msg.addParameter("kill", kill);
        }
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'user' message.
     */
    static JSONLiteral msgUser(Referenceable target, String userName,
                               boolean online, JSONLiteralArray contexts)
    {
        JSONLiteral msg = new JSONLiteral(target, "user");
        msg.addParameter("user", userName);
        msg.addParameter("on", online);
        msg.addParameterOpt("contexts", contexts);
        msg.finish();
        return msg;
    }
}
