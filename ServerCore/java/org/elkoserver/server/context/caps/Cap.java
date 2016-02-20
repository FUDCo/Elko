package org.elkoserver.server.context.caps;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.context.BasicObject;
import org.elkoserver.server.context.Item;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.ObjectCompletionWatcher;
import org.elkoserver.server.context.User;

/**
 * Base class for implementing capability mods that grant access to privileged
 * operations.
 */
abstract public class Cap extends Mod implements ObjectCompletionWatcher {

    /** Scope flag: if true, holder can transfer the capability to another
        holder. */
    private boolean amTransferrable;

    /** Scope flag: if true, holder can delete this capability. */
    private boolean amDeletable;

    /** Expiration time.  After this time, this capability will no longer
     * function.  Value is system time in milliseconds.  A value of 0 indicates
     * that the capability never expires.  A value of -1 indicates that the
     * capability is ephemeral, i.e., that it expires when the holder exits.
     */
    private long myExpiration;

    /**
     * JSON-driven constructor.
     *
     * Note that there are no JSON parameters declared.  This constructor must
     * be called from the subclass and will extract its own parameters from the
     * JSON object directly.  It looks for three parameters, two booleans and a
     * timestamp:
     *
     *    transferrable  true (default) if cap is transferrable
     *    deletable      true (default) if cap is deletable
     *    expiration     time after which cap no longer operates (default
     *                   value is 0, i.e., cap is permanent), expressed as
     *                   milliseconds past the epoch, exactly as returned by
     *                   {@link System#currentTimeMillis}.
     *
     * @param desc  The parsed JSON object describing this capability mod.
     */
    public Cap(JSONObject desc) throws JSONDecodingException {
        amTransferrable = desc.optBoolean("transferrable", true);
        amDeletable     = desc.optBoolean("deletable", true);
        myExpiration    = desc.optLong("expiration", 0);
    }

    /**
     * Mark the item as visible only to its holder.
     *
     * <p>Application code should not call this method.
     */
    final public void objectIsComplete() {
        BasicObject obj = object();
        if (obj instanceof Item) {
            obj.setVisibility(BasicObject.VIS_PERSONAL);
        }
    }

    /**
     * Encode the basic capability parameters as part of encoding this
     * capability mod.  This method must be called by the encode() method of
     * each subclass.
     *
     * @param lit  The JSONLiteral into which this capability mod is being
     *    encoded.
     */
    final protected void encodeDefaultParameters(JSONLiteral lit) {
        if (!amDeletable) {
            lit.addParameter("deletable", amDeletable);
        }
        if (!amTransferrable) {
            lit.addParameter("transferrable", amTransferrable);
        }
        if (myExpiration > 0) {
            lit.addParameter("expiration", myExpiration);
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to a capability that is actually available to that
     * user.  To be available, the capability must be attached to an object
     * that is reachable by the user and it must not be expired.  If this
     * capability is not available, this method with throw a {@link
     * MessageHandlerException}.
     *
     * @throws MessageHandlerException if the test fails.
     */
    final public void ensureValid(User from) throws MessageHandlerException {
        ensureReachable(from);
        if (isExpired()) {
            throw new MessageHandlerException("capability expired");
        }
    }

    /**
     * Test if this capability has expired.
     *
     * @return true if this capability has expired, false if it is still OK to
     *    use.
     */
    final public boolean isExpired() {
        return 0 < myExpiration && myExpiration < System.currentTimeMillis();
    }

    /**
     * Handle a 'delete' message.  This is a request from a client to delete
     * the capability object.
     *
     * <p>This request will be rejected if the capability is marked as
     * undeletable and is not expired.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"delete" } </tt><br>
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"delete" } </tt>
     *
     * @param from  The user who sent the message.
     */
    @JSONMethod
    final public void delete(User from) throws MessageHandlerException {
        ensureReachable(from);
        if (!amDeletable && !isExpired()) {
            throw new MessageHandlerException(
                "attempt to delete non-deletable capability");
        }

        Item cap = (Item) object();
        ((Deliverer) holder()).send(Msg.msgDelete(cap));
        cap.delete();
    }

    /**
     * Handle a 'setlabel' message.  This is a request from a client to change
     * the label of the capability object.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"setlabel",
     *                     label:<i>STR</i> } </tt><br>
     * <u>send</u>: no reply is sent.
     *
     * @param from  The user who sent the message.
     * @param label  The new label string.
     */
    @JSONMethod({ "label" })
    public void setlabel(User from, String label)
        throws MessageHandlerException
    {
        ensureReachable(from);
        object().setName(label);
    }

    /**
     * Handle a 'transfer' message.  This is a request from a client to pass
     * possession of the capability object to somebody else.
     *
     * <p>This request will be rejected if the capability is marked as
     * untransferrable.  Yes, this is unenforceable due to the possibility of
     * proxying, but marketing solipsism must be served.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"transfer", dest:<i>DESTREF</i> } </tt><br>
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"delete" } </tt><br>
     * <u>send</u>: <tt> { to:<i>DESTREF</i>, op:"make", ... } </tt>
     *
     * @param from  The user who sent the message.
     * @param destRef  Reference to user or context that is to receive the
     *    capability.
     */
    @JSONMethod({ "dest" })
    final public void transfer(User from, String destRef)
        throws MessageHandlerException
    {
        ensureReachable(from);
        if (!amTransferrable) {
            throw new MessageHandlerException(
                "attempt to transfer non-transferrable capability");
        }

        BasicObject newHolder = context().get(destRef);
        if (newHolder == null || newHolder instanceof Item) {
            // XXX TODO IMPORTANT: doesn't work if dest is offline
            throw new MessageHandlerException("invalid transfer destination " +
                                              destRef);
        }
        Item cap = (Item) object();
        ((Deliverer) holder()).send(Msg.msgDelete(cap));
        cap.setContainer(newHolder);
        cap.sendObjectDescription((Deliverer) newHolder, newHolder);
    }

    /**
     * Handle a 'spawn' message.  This is a request from a client to create a
     * copy of the capability object, with possibly reduced scope of powers.
     *
     * <p>This method will reject, as an illegal rights amplification, any
     * attempt to spawn a capability that is undeletable when the base
     * capability is deletable, transferrable when the base capability is
     * untransferrable, or which has a later expiration time than the base
     * capability.
     *
     * <p>If 'dest' designates a different holder, the operation will also be
     * regarded as a transfer and subjected to all the same checks as a call to
     * {@link #transfer transfer()}.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"spawn", dest:<i>optDESTREF</i>,
     *                     transferrable:<i>optBOOL</i>,
     *                     duration:<i>optLONG</i>,
     *                     expiration:<i>optLONG</i> } </tt><br>
     * <u>send</u>: <tt> { to:<i>DESTREF</i>, op:"make", ... } </tt>
     *
     * @param from  The user who sent the message.
     * @param dest  Reference to container into which the new capability is to
     *    be placed.  If omitted, defaults to the sending user.  If this
     *    capability is non-transferrable, must refer to the sending user or a
     *    container contained by the sending user.
     * @param transferrable  Flag indicating whether the new capability is to
     *    be transferrable.  If omitted, defaults to the same value as this
     *    capability.  It is not permitted to set this parameter to
     *    true if this capability is not itself transferrable.
     * @param deleteable  Flag indicating whether the new capability is to be
     *    deleteable.  If omitted, defaults to true.  It is not permitted to
     *    set this parameter to false if this capability is itself deletable.
     * @param duration  Expiration time of the new capability, expressed as an
     *    offset (in milliseconds) from the present.  A value of 0 (the
     *    default) indicates that the capability is permanent.
     * @param expiration  Expiration time of the new capability, expressed as
     *    an absolute system time (in milliseconds).
     */
    @JSONMethod({ "dest", "transferrable", "deletable", "duration",
                  "expiration"})
    public void spawn(User from, OptString dest, OptBoolean transferrable,
                      OptBoolean deleteable, OptInteger duration,
                      OptInteger expiration)
        throws MessageHandlerException
    {
        ensureReachable(from);

        String destRef = dest.value(null);
        BasicObject container;
        if (destRef == null) {
            container = from;
        } else {
            container = context().get(destRef);
        }
        if (container == null) {
            throw new MessageHandlerException("can't find spawn destination " +
                                              destRef);
        }
        boolean newTransferrable = transferrable.value(amTransferrable);
        boolean newDeletable = transferrable.value(true);
        if ((newTransferrable && !amTransferrable) ||
                (!newDeletable && amDeletable)) {
            throw new MessageHandlerException("illegal rights amplification");
        }
        if (!amTransferrable && container.holder() != from) {
            throw new MessageHandlerException(
                "capability is not transferrable");
        }

        long newDuration = duration.value(0);
        long newExpiration = expiration.value(0);
        if (newExpiration == 0 && newDuration == 0) {
            newExpiration = myExpiration;
        } else if (newExpiration == 0) { /* newDuration != 0 */
            newExpiration = newDuration + System.currentTimeMillis();
        } else if (newDuration != 0) { /* && newExpiration != 0 */
            throw new MessageHandlerException(
                "can't specify both duration and expiration");
        }
        boolean expireOK;
        if (myExpiration == 0) {
            expireOK = true;
        } else if (myExpiration == -1) {
            expireOK = (newExpiration == -1);
        } else {
            expireOK = (newExpiration <= myExpiration);
        }
        if (!expireOK) {
            throw new MessageHandlerException("illegal rights amplification");
        }

        Item capItem = container.createItem(object().name(), false, true);
        Cap cap = null;
        try {
            cap = (Cap) clone();
        } catch (CloneNotSupportedException e) {
            throw new MessageHandlerException("this can't happen");
        }
        cap.amTransferrable = newTransferrable;
        cap.amDeletable = newDeletable;
        cap.myExpiration = newExpiration;
        cap.attachTo(capItem);
        capItem.objectIsComplete();
        capItem.sendObjectDescription((Deliverer) capItem.holder(), container);
    }
}
