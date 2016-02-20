package org.elkoserver.server.director;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Singleton handler for the director 'provider' protocol.
 *
 * The 'provider' protocol consists of these requests:
 *
 *   'address' - Reports that the provider is speaking a particular protocol
 *       at a particular address and port number.
 *
 *   'context' - Reports that a particular context has been opened or closed by
 *       the sending provider.
 *
 *   'gate' - Reports that a particular context's gate has been opened or
 *       closed by the sending provider.
 *
 *   'load' - Reports the provider's current load factor to the director.
 *
 *   'relay' - Requests the director to deliver an arbitrary message to a
 *      context, context family, or user, by relaying through the appropriate
 *      provider servers for the message target's current location.
 *
 *   'user' - Reports that a particular user has arrived or departed from a
 *      context provided by the sending provider.
 *
 *   'willserve' - Reports the provider's willingness to serve a particular
 *      context family.
 */
class ProviderHandler extends UserHandler {
    /**
     * Constructor.
     *
     * @param director  The Director object for this handler.
     */
    ProviderHandler(Director director) {
        super(director);
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "provider";
    }

    /**
     * Handle the 'address' verb.
     *
     * Note the availability of a provider server speaking some protocol at
     * some network address.
     *
     * @param from  The provider server announcing its availability.
     * @param protocol  The protocol it will server it with.
     * @param hostPort  Where to connect for service.
     */
    @JSONMethod({ "protocol", "hostport" })
    public void address(DirectorActor from, String protocol, String hostPort)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        from.provider().addProtocol(protocol, hostPort);
    }

    /**
     * Handle the 'context' verb.
     *
     * Note the opening or closing of a context.
     *
     * @param from  The provider server announcing the context change.
     * @param context  The context that opened or closed.
     * @param open  true if context opened, false if it closed.
     * @param mine  true if this director was the one that told it to open.
     * @param optMaxCapacity  The optional maximum user capacity for the
     *    context.
     * @param optBaseCapacity  The optional base capacity for the (clone)
     *    context.
     * @param optRestricted  Optional flag indicating whether or not the
     *    context is restricted (unrestricted by default).
     */
    @JSONMethod({ "context", "open", "yours", "maxcap", "basecap",
                  "restricted" })
    public void context(DirectorActor from, String context, boolean open,
                        boolean mine, OptInteger optMaxCapacity,
                        OptInteger optBaseCapacity, OptBoolean optRestricted)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        if (open) {
            int maxCapacity = optMaxCapacity.value(-1);
            int baseCapacity = optBaseCapacity.value(maxCapacity);
            boolean restricted = optRestricted.value(false);
            from.provider().noteContextOpen(context, mine, maxCapacity,
                                            baseCapacity, restricted);
        } else {
            from.provider().noteContextClose(context);
        }
    }

    /**
     * Handle the 'gate' verb.
     *
     * Note the opening or closing of a context's gate.
     *
     * @param from  The provider server announcing the change
     * @param context  The context whose gate is being opened or closed
     * @param open  true if the gate is opening, false if it is closing
     * @param optReason  Optional reason string indicating why the gate is
     *    closed (ignored for opening).  This will be reported to users who
     *    attempt to enter when they fail.
     */
    @JSONMethod({ "context", "open", "reason" })
    public void gate(DirectorActor from, String context, boolean open,
                     OptString optReason)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        from.provider().noteContextGateSetting(context, open,
                                               optReason.value(null));
    }

    /**
     * Handle the 'load' verb.
     *
     * Note a provider's load factor.
     *
     * @param from  The provider server announcing its load.
     * @param factor  The load factor.
     */
    @JSONMethod({ "factor" })
    public void load(DirectorActor from, double factor)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        from.provider().setLoadFactor(factor);
    }

    /**
     * Handle the 'relay' verb.
     *
     * Request that a message be relayed to an user or context.
     *
     * @param from  The provider sending the message.
     * @param context  The context to be broadcast to.
     * @param user  The user to be broadcast to.
     * @param msg  The message to relay to them.
     */
    @JSONMethod({ "context", "user", "msg" })
    public void relay(DirectorActor from, OptString context, OptString user,
                      JSONObject msg)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        director().doRelay(from, context, user, msg);
    }

    /**
     * Handle the 'user' verb.
     *
     * Note the arrival or departure of an user.
     *
     * @param from  The provider server announcing the context change.
     * @param context  The context that the user entered or exited.
     * @param user  The user who entered or exited.
     * @param on  true on entry, false on exit.
     */
    @JSONMethod({ "context", "user", "on" })
    public void user(DirectorActor from, String context, String user,
                      boolean on)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        if (on) {
            from.provider().noteUserEntry(context, user);
        } else {
            from.provider().noteUserExit(context, user);
        }
    }

    /**
     * Handle the 'willserve' verb.
     *
     * Note the availability of a provider to serve some class of contexts.
     *
     * @param from  The provider server announcing its availability.
     * @param context  The context family it will serve.
     * @param capacity  Optional limit on the number of clients it will serve.
     * @param restricted  Optional flag restricting reservations for this
     *    class of contexts to reservations made internally.
     */
    @JSONMethod({ "context", "capacity", "restricted" })
    public void willserve(DirectorActor from, String context,
                          OptInteger capacity, OptBoolean restricted)
        throws MessageHandlerException
    {
        from.ensureAuthorizedProvider();
        from.provider().addService(context, capacity.value(-1),
                                   restricted.value(false));
    }
}
