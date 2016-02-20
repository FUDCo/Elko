package org.elkoserver.server.context;

import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;

/**
 * Abstract base class to facilitate implementation of application-specific
 * mods that can be attached to basic objects (contexts, users, and items).
 *
 * This class implements basic services needed by or useful to all mods,
 * regardless of application.
 *
 * Subclasses need to implement application-specific mod logic as well as the
 * {@link Encodable#encode encode()} method called for by the {@link
 * Encodable} interface.
 */
abstract public class Mod implements Encodable, DispatchTarget, Cloneable {
    /** The object to which this Mod is attached, or null if unattached. */
    private BasicObject myObject;

    /** Flag indicating that this mod disappears when leaving the context or
        when the object to which it is attached is persisted. */
    private boolean amEphemeral;

    /**
     * Base constructor.
     */
    protected Mod() {
        myObject = null;
        amEphemeral = false;
    }

    /**
     * Attach this mod to an object.
     *
     * <p>Only one mod of any given class may be attached to any given object.
     *
     * <p>Application code will not normally need to call this method, since it
     * is called automatically when an object is loaded from persistent
     * storage.  However, certain specialized applications that synthesize
     * objects directly will need to use this to attach the mods they have
     * constructed to the objects they have constructed.
     *
     * @param object  The object to which this mod is to be attached.
     */
    public void attachTo(BasicObject object) {
        myObject = object;
        object.attachMod(this);
    }

    /**
     * Clone this object.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to an object that that user is allowed to reach (either
     * because it is in the context or because the user is holding it).  If
     * this mod is not attached to such an object, this method will throw a
     * {@link MessageHandlerException} exception.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected void ensureReachable(User who) throws MessageHandlerException {
        User holder = myObject.user();
        if (holder != who) {
            if (holder == null) {
                ensureSameContext(who);
            } else {
                throw new MessageHandlerException("user " + who +
                    " attempted operation on non-reachable object " +
                    myObject);
            }
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to an object that that user is holding.  If this mod is
     * not attached to such an object, this method will throw a {@link
     * MessageHandlerException} exception.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected void ensureHolding(User who) throws MessageHandlerException {
        User holder = myObject.user();
        if (holder != who) {
            throw new MessageHandlerException("user " + who +
                " attempted operation on non-held object " + myObject);
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to that same user.  If this mod is not attached to
     * that user, this method will throw a {@link MessageHandlerException}.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected void ensureSameUser(User who) throws MessageHandlerException {
        if (who != myObject) {
            throw new MessageHandlerException("user " + who +
                                              " attempted operation on " +
                                              myObject + " instead of self");
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object is taking place in the same context as the object.  If this
     * mod is not attached to an object in the same context as the user, this
     * method will throw a {@link MessageHandlerException}.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected void ensureSameContext(User who) throws MessageHandlerException {
        myObject.ensureSameContext(who);
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object that is contained by the user's context.  If this mod is
     * not attached to such an object, this method will throw a {@link
     * MessageHandlerException}.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    protected void ensureInContext(User who) throws MessageHandlerException {
        if (who.context() != myObject.container() &&
                who.context() != myObject) {
            throw new MessageHandlerException("user " + who +
                " attempted operation on object " + myObject +
                " that is not in the user's context");
        }
    }

    /**
     * Obtain the user or context holding the object to which this mod is
     * attached, regardless of how deeply nested in containers it might be.
     *
     * @return the user or context in which the object that mod is attached to
     *    is located, at whatever level of container nesting, or null if it is
     *    not held by anything.
     */
    protected BasicObject holder() {
        return myObject.holder();
    }

    /**
     * Test if this mod is ephemeral.  If a mod is ephemeral, its state is
     * not persisted.  A mod is made ephemeral by calling the {@link
     * #markAsEphemeral} method.
     *
     * @return true if the mod is ephemeral, false if not.
     */
    public boolean isEphemeral() {
        return amEphemeral;
    }

    /**
     * Mark the object to which this mod is attached as having been changed and
     * thus in need of checkpointing.
     */
    public void markAsChanged() {
        myObject.markAsChanged();
    }

    /**
     * Mark this mod as being ephemeral.
     */
    public void markAsEphemeral() {
        amEphemeral = true;
    }

    /**
     * Obtain the object to which this mod is attached.
     *
     * @return the object to which this mod is attached.
     */
    public BasicObject object() {
        return myObject;
    }

    /**
     * Obtain the context in which the object this mod is attached to is
     * located, regardless of how deeply nested in containers the object might
     * be.
     *
     * @return the context in which this mod is located, or null if it is not
     *    in any context.
     */
    public Context context() {
        return myObject.context();
    }
}
