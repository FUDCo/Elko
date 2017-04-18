package org.elkoserver.server.context;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.elkoserver.foundation.json.DefaultDispatchTarget;
import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.DispatchTarget;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.MessageRetargeter;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Base class of the fundamental addressable objects in the Context Server:
 * context, user, and item.
 *
 * <p>All such objects share many characteristics in common, hence this base
 * class.  They are all {@link Encodable} since representations of them can be
 * sent to the client, {@link DispatchTarget}s since the client can address
 * messages to them, {@link MessageRetargeter}s since they can have {@link
 * Mod}s attached and so need to be able to retarget arriving messages to those
 * mods, and {@link Referenceable} since they can be referred to in messages.
 */
abstract public class BasicObject
    implements DefaultDispatchTarget,
               DispatchTarget,
               Encodable,
               MessageRetargeter,
               Referenceable
{
    /** Flag that this object needs to be checkpointed to the database. */
    private boolean amChanged;

    /** Flag that this object has been deleted. */
    private boolean amDeleted;

    /** Flag that this object is ephemeral. */
    private boolean amEphemeral;

    /** Other objects that should be checkpointed when this object is. */
    private List<BasicObject> myCodependents;

    /* Note: various fields below are marked as 'protected' with the keyword
       commented out.  This is because they really want to be both protected
       and package scoped, but Java doesn't have that -- it has to be one or
       the other.  The tie breaker was JavaDoc: it will put protected fields
       into the JavaDoc output, which would be bad because these are not part
       of the official interface. */

    /** Reference string for this object. */
    /* protected */ String myRef;

    /** Object name. */
    /* protected */ String myName;

    /** Objects contained by this object. */
    /* protected */ Contents myContents;

    /** Position of object with respect to its container, or null if position
        is unknown or irrelevant. */
    /* protected */ Position myPosition;

    /** Mods attached to this object. */
    /* protected */ ModSet myModSet;

    /** The contextor for this server. */
    /* protected */ Contextor myContextor;

    /** Optional handler for messages that don't have handlers. */
    private DefaultDispatchTarget myDefaultDispatchTarget;

    /** Optional watcher for contents updates. */
    private ContentsWatcher myContentsWatcher;

    /** Indicator of visibility rules to apply to this object. */
    private int myVisibility;

    /** Count of outstanding initialization events pending before object is
        considered fully loaded. */
    private int myUnfinishedInitCount;

    /** Visibility has not been set. */
    private static final int VIS_DEFAULT = 0;

    /** Visible to everyone in the enclosing context. */
    public static final int VIS_PUBLIC = 1;

    /** Visible to user holding object but nobody else. */
    public static final int VIS_PERSONAL = 2;

    /** Not visible to anyone. */
    public static final int VIS_NONE = 3;

    /** Visible according to the visibility of its container. */
    public static final int VIS_CONTAINER = 4;

    /** Inactive content items, prior to activating this object. */
    private Item myPassiveContents[];

    /**
     * Constructor.
     *
     * @param name  The name of the object (mainly for diagnostic messages).
     * @param mods  Array of mods to attach to the object; can be null if no
     *    mods are to be attached at initial creation time.
     * @param isContainer  true if this object is allowed to be a container.
     * @param contents  Array of inactive items that will be the initial
     *    contents of this object, or null if there are no contents now.
     * @param pos  Position of this object with respect to its container, or
     *    null if unknown or irrelevant.
     */
    BasicObject(String name, Mod mods[], boolean isContainer, Item contents[],
                Position pos) {
        myName = name;
        myCodependents = null;
        myDefaultDispatchTarget = null;
        myContentsWatcher = null;
        myVisibility = VIS_DEFAULT;
        if (isContainer || contents != null) {
            myContents = null;
        } else {
            myContents = Contents.theVoidContents;
        }
        myPassiveContents = contents;
        myModSet = new ModSet(mods);
        myPosition = pos;
        amChanged = false;
        amDeleted = false;
        amEphemeral = false;
        myUnfinishedInitCount = 0;
    }

    void addPassiveContents(Item[] contents) {
        if (myPassiveContents == null) {
            myPassiveContents = contents;
        } else {
            Item[] newPassiveContents =
                new Item[myPassiveContents.length + contents.length];
            System.arraycopy(myPassiveContents, 0, newPassiveContents, 0,
                             myPassiveContents.length);
            System.arraycopy(contents, 0,
                             newPassiveContents, myPassiveContents.length,
                             contents.length);
            myPassiveContents = newPassiveContents;
        }
    }

    void notePendingInit() {
        ++myUnfinishedInitCount;
    }

    boolean isReady() {
        return myUnfinishedInitCount <= 0;
    }

    void resolvePendingInit() {
        --myUnfinishedInitCount;
        if (myUnfinishedInitCount <= 0) {
            myContextor.resolvePendingInit(this);
        }
    }

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object.
     * @param subID  Clone sub identity, or the empty string for non-clones.
     * @param isEphemeral  True if this object is ephemeral (won't checkpoint).
     * @param contextor  The contextor for this server.
     */
    void activate(String ref, String subID, boolean isEphemeral,
                  Contextor contextor)
    {
        myRef = ref;
        if (isEphemeral) {
            markAsEphemeral();
        }
        myContextor = contextor;
        if (ref != null) {
            contextor.addRef(this);
        }
        if (myModSet != null) {
            myModSet.attachTo(this);
        }
        activatePassiveContents(subID);
    }

    /**
     * Move contents items from the passive contents array to the actual live
     * contents array and make them live too.
     *
     * @param subID  Clone sub identity, or the empty string for non-clones.
     */
    void activatePassiveContents(String subID) {
        myContextor.setContents(this, subID, myPassiveContents);
        myPassiveContents = null;
    }

    /**
     * Add an item to this object's contents.
     *
     * @param item  The item to add.
     */
    void addToContents(Item item) {
        myContents = Contents.withContents(myContents, item);
        if (myContentsWatcher != null) {
            myContentsWatcher.noteContentsAddition(item);
        }
    }

    /**
     * Add a new mod to this.
     *
     * @param mod  The mod to attach.
     */
    void attachMod(Mod mod) {
        myModSet = ModSet.withMod(myModSet, mod);
        if (myContextor != null) {
            myContextor.addClass(mod.getClass());
        }
        if (mod instanceof DefaultDispatchTarget) {
            if (myDefaultDispatchTarget == null) {
                myDefaultDispatchTarget = (DefaultDispatchTarget) mod;
            } else {
                context().trace().errorm("DefaultDispatchTarget mod " + mod +
                    " added to " + this + ", which already has one");
            }
        }
        if (mod instanceof ContentsWatcher) {
            if (myContentsWatcher == null) {
                myContentsWatcher = (ContentsWatcher) mod;
            } else {
                context().trace().errorm("ContentsWatcher mod " + mod +
                    " added to " + this + ", which already has one");
            }
        }
    }

    /**
     * Obtain an object clone's base reference.  This is the object reference
     * string with the clone sub-ID stripped off.
     *
     * @return the base reference string for this object, if it is a clone.  If
     *    it is not a clone, the reference string itself will be returned.
     */
    public String baseRef() {
        return Contextor.extractBaseRef(myRef);
    }

    /**
     * Checkpoint this object, its contents, and any registered codependent
     * objects (that is, objects whose state must be kept consistent with this
     * object and vice versa).  In other words, ensure that any changes to the
     * aforementioned objects' states that have been made since the last time
     * they were checkpointed are saved to persistent storage.
     */
    public void checkpoint() {
        checkpoint(null);
    }

    /**
     * Checkpoint this object, with completion handler.
     *
     * Note that the completion handler is run when the write of the object
     * itself completes; execution of the completion handler does not indicate
     * that the object's contents or codedependent objects have yet been
     * written!
     *
     * @param handler  Optional completion handler.
     */
    public void checkpoint(ArgRunnable handler) {
        if (!amEphemeral) {
            checkpointSelf(handler);
            if (myCodependents != null) {
                List<BasicObject> codependents = myCodependents;
                myCodependents = null;
                for (BasicObject codep : codependents) {
                    codep.checkpoint();
                }
            }
        } else {
            if (handler != null) {
                handler.run(null);
            }
        }
    }

    /**
     * Write this object and all of its contents (recursively) to the object
     * database if they have changed.
     *
     * @param handler  Optional completion handler.
     */
    private void checkpointSelf(ArgRunnable handler) {
        if (!amEphemeral) {
            if (myContents != null) {
                for (BasicObject item : myContents) {
                    item.checkpointSelf(null);
                }
            }
            doCheckpoint(handler);
        } else {
            if (handler != null) {
                handler.run(null);
            }
        }
    }

    /**
     * Checkpoint this object and any registered codependent objects.  However,
     * don't bother to checkpoint the objects' contents.
     *
     * This is an optimization used when checkpointing the complete collection
     * of objects in a context.  In such a case, the various objects' contents
     * trees do not need to be walked, since the initiator of the checkpoint
     * will be visiting them all anyway.  However, codependent objects *do*
     * need to be visited since they might not be in the same context.
     */
    void checkpointWithoutContents() {
        if (!amEphemeral) {
            doCheckpoint(null);
            if (myCodependents != null) {
                for (BasicObject codep : myCodependents) {
                    codep.checkpoint();
                }
                myCodependents = null;
            }
        }
    }

    /**
     * Get this objects's container.  For objects not currently in any
     * container (including non-containable objects), this will be null.  The
     * base case is that the object is not containable (contexts and users are
     * never containable, while items may be), so this base implementation will
     * always return null.
     *
     * @return the object this object is currently contained by.
     */
    public BasicObject container() {
        return null;
    }

    /**
     * Obtain an iterable for this object's contents.  If the object has no
     * contents, either because it is empty or because it is not a container,
     * the iterable returned will be empty (i.e., its iterator's {@link
     * java.util.Iterator#hasNext hasNext()} method will return false right
     * away) but null will never be returned.
     *
     * @return an iterable that iterates over this object's contents.
     */
    public Iterable<Item> contents() {
        if (myContents == null) {
            return Collections.emptyList();
        } else {
            return myContents;
        }
    }

    /**
     * Obtain the context in which this object is located, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the context in which this object is located, at whatever level
     *    of container nesting, or null if it is not in any context.
     */
    abstract public Context context();

    /**
     * Obtain the contextor that created this object.
     *
     * @return the contextor associated with this object.
     */
    public Contextor contextor() {
        return myContextor;
    }

    /**
     * Create a {@link Item} directly (i.e., create it at runtime rather than
     * loading it from the database).  The new item will be contained by this
     * object and have neither any contents nor any mods.
     *
     * @param name  The name for the new item, or null if the name doesn't
     *    matter.
     * @param isPossibleContainer  true if the new item may itself be used as a
     *    container, false if not.
     * @param isDeletable  true if users may delete the new item at will, false
     *    if not.
     *
     * @return a new {@link Item} object as described by the parameters.
     */
    public Item createItem(String name, boolean isPossibleContainer,
                           boolean isDeletable)
    {
        return myContextor.createItem(name, this, isPossibleContainer,
                                      isDeletable);
    }

    /**
     * Do the actual work of writing this object's changed state to the object
     * database, if its state has actually changed.
     *
     * @param handler  Optional completion handler
     */
    private void doCheckpoint(ArgRunnable handler) {
        if (amChanged) {
            amChanged = false;
            if (amDeleted) {
                myContextor.writeObjectDelete(baseRef(), handler);
            } else {
                myContextor.writeObjectState(baseRef(), this, handler);
            }
        } else {
            if (handler != null) {
                handler.run(null);
            }
        }
    }

    /**
     * Remove this object's contents (and their contents, recursively) from
     * the working set of objects in memory.
     */
    void dropContents() {
        for (Item item : contents()) {
            item.dropContents();
            myContextor.remove(item);
        }
        myContents = null;
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * on an object is taking place in the same context as the object.
     *
     * @param who  The user who is attempting the operation.
     *
     * @throws MessageHandlerException if the test fails.
     */
    void ensureSameContext(User who) throws MessageHandlerException {
        if (context() != who.context()) {
            throw new MessageHandlerException("user " + who +
                                              " attempted operation on " +
                                              this + " outside own context");
        }
    }

    /**
     * Obtain one of this object's {@link Mod}s.
     *
     * @param type  The type of the mod desired.
     *
     * @return the mod of the given type, if there is one, else null.
     */
    public Mod getMod(Class type) {
        if (myModSet == null) {
            return null;
        } else {
            return myModSet.getMod(type);
        }
    }

    /**
     * Obtain the user or context holding this object, regardless of how deeply
     * nested in containers it might be.  The base case is that this object is
     * not held.
     *
     * @return the user or context within which this object is contained, at
     *    whatever level of container nesting, or null if it is not held by
     *    anything.
     */
    public BasicObject holder() {
        return null;
    }

    /**
     * Test if this object is a clone.
     *
     * @return true if this object is a clone object, false if not.
     */
    public boolean isClone() {
        return myRef.indexOf('-') != myRef.lastIndexOf('-');
    }

    /**
     * Test if this object is allowed to be used as a container.
     *
     * @return true if this object can contain other objects, false if not.
     */
    public boolean isContainer() {
        return myContents != Contents.theVoidContents;
    }

    /**
     * Test if this object is ephemeral.  If an object is ephemeral, its state
     * is not persisted.  An object is made ephemeral by calling the {@link
     * #markAsEphemeral} method.
     *
     * @return true if the object is ephemeral, false if not.
     */
    public boolean isEphemeral() {
        return amEphemeral;
    }

    /**
     * Mark this object as needing to be written to persistent storage.  Its
     * state and the state of any codependent objects will be written out the
     * next time the object is checkpointed.
     */
    public void markAsChanged() {
        amChanged = true;
    }

    /**
     * Mark this object as having been deleted.  Note that unlike {@link
     * #markAsChanged}, there is no corresponding method to unmark deletion;
     * this is a one-way trip.
     */
    public void markAsDeleted() {
        amChanged = true;
        amDeleted = true;
    }

    /**
     * Mark this object as being ephemeral.
     */
    public void markAsEphemeral() {
        amEphemeral = true;
    }

    /**
     * Obtain this object's name, if it has one.
     *
     * @return this object's name, or null if it is nameless.
     */
    public String name() {
        return myName;
    }

    /**
     * Note another object that needs to be checkpointed when this object is
     * checkpointed (in order to maintain data consistency).  An object may
     * have any number of codependents.
     *
     * @param obj  The other, codependent object.
     */
    public void noteCodependent(BasicObject obj) {
        if (myCodependents == null) {
            myCodependents = new LinkedList<BasicObject>();
        }
        myCodependents.add(obj);
    }

    /**
     * Inform this object that its construction is now complete.  This will in
     * turn inform any {@link Mod}s that have expressed an interest in this
     * event so that they can do any post-creation cleanup or initialization.
     *
     * <p>Application code should not normally need to call this method, since
     * it is called automatically when an object is loaded from persistent
     * storage.  However, certain specialized applications that synthesize
     * objects directly will need to call this after they finish attaching any
     * synthesized {@link Mod}s.
     */
    public void objectIsComplete() {
        if (myModSet != null) {
            myModSet.objectIsComplete();
        }
    }

    /**
     * Obtain this object's position with respect to its container.
     *
     * @return this object's position, or null if it has none.
     */
    public Position position() {
        return myPosition;
    }

    /**
     * Remove an item from this object's contents
     *
     * Note: this method is for use by the implementation of the containership
     * mechanism and should never be called directly.  Instead, use
     * item.setContainer().
     *
     * @param item  The item to remove
     */
    void removeFromContents(Item item) {
        if (myContentsWatcher != null) {
            myContentsWatcher.noteContentsRemoval(item);
        }
        myContents = Contents.withoutContents(myContents, item);
    }
    
    /**
     * Transmit a description of this object as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    abstract public void sendObjectDescription(Deliverer to,
                                               Referenceable maker);

    /**
     * Send a JSON message to all other clones of this object.  The message
     * will be delivered and dispatched to each clone as if it had been
     * received from a client (except that the 'from' parameter will be
     * null).
     *
     * If this Context Server is connected to a Director, the message will be
     * relayed to the Director that will relay it in turn to any clones of this
     * object that reside on other Context Servers connected to that Director.
     * Note that this can be a very expensive operation if used injudiciously.
     *
     * This operation is only valid for users and contexts; it does not work on
     * items.  Also, if this object is not a clone, this method will have no
     * effect.
     *
     * @param message  The message to send.
     */
    public void sendToClones(JSONLiteral message) {
        myContextor.relay(this, message);
    }

    /**
     * Set this object's name.
     *
     * @param name  The new name for the object to have.
     */
    public void setName(String name) {
        myName = name;
        markAsChanged();
    }

    /**
     * Set this object's position.
     *
     * @param pos  New position for the object.
     */
    public void setPosition(Position pos) {
        myPosition = pos;
        markAsChanged();
    }

    /**
     * Set this object's visibility without taking any other action.  This can
     * only be done once.
     *
     * An object's visibility determines the circumstances under which a
     * description of the object will be transmitted to clients as part of the
     * description of the containing context.  Four cases are supported:
     *
     * {@link #VIS_PUBLIC} indicates that the object is visible to anyone in
     * the context, i.e., its description will always be transmitted.
     *
     * {@link #VIS_PERSONAL} indicates that the object's description will only
     * be sent to the user who is holding it and not to anyone else.
     *
     * {@link #VIS_NONE} indicates that the object's description will never be
     * sent to anyone.
     *
     * {@link #VIS_CONTAINER} indicates that the object inherits its visiblity
     * from its container, i.e., its description will be transmitted to anyone
     * to whom its container's description is transmitted.
     *
     * If the visibility is not set by calling this method, a default
     * visibility rule will be applied, which is equivalent in every way to
     * {@link #VIS_PUBLIC} except that it may be modified by calling this
     * method.  Note also that users and contexts are always implicitly set to
     * {@link #VIS_PUBLIC} regardless.
     *
     * @param visibility  The visibility setting.  This should be one of the
     *    constants {@link #VIS_PUBLIC}, {@link #VIS_PERSONAL}, {@link
     *    #VIS_NONE}, or {@link #VIS_CONTAINER}.
     */
    public void setVisibility(int visibility) {
        if (myVisibility == VIS_DEFAULT) {
            myVisibility = visibility;
        } else if (myVisibility != visibility) {
            throw new Error("duplicate visibility setting");
        }
    }

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object.
     */
    abstract String type();

    /**
     * Obtain the user within which this object is contained, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the user in which this object is contained, at whatever level of
     *    container nesting, or null if it is not contained by a user.
     */
    abstract public User user();

    /**
     * Test if this object is visible to a given receiver.  If the receiver is
     * a {@link User}, then this will test the visibility of the object to that
     * particular user.  If the receiver is a {@link Context}, then it will
     * test the visibility of the object to any user in that context.
     *
     * @param receiver  User or context whose sightlines are at issue.
     *
     * @return true if this object is visible to 'receiver', false if not.
     */
    public boolean visibleTo(Deliverer receiver) {
        switch (myVisibility) {
            case VIS_PUBLIC:
            case VIS_DEFAULT:
                return true;
            case VIS_PERSONAL:
                return user() == receiver;
            case VIS_CONTAINER:
                BasicObject cont = container();
                if (cont == null) {
                    /* If there is no container, then this is a user or a
                       context and thus visible. */
                    return true;
                } else {
                    return cont.visibleTo(receiver);
                }
            case VIS_NONE:
                return false;
            default:
                throw new Error("invalid visibility value in " + this + ": " +
                                myVisibility);
        }
    }

    /* ----- DefaultDispatchTarget interface ------------------------------- */

    /**
     * Handle an otherwise unhandled message.
     *
     * @param from  Who sent the message.
     * @param msg  The message itself.
     *
     * @throws MessageHandlerException if there was a problem handling the
     *    message.
     */
    public void handleMessage(Deliverer from, JSONObject msg)
        throws MessageHandlerException
    {
        if (myDefaultDispatchTarget != null) {
            myDefaultDispatchTarget.handleMessage(from, msg);
        } else {
            throw new MessageHandlerException(
                "no message handler method for verb '" + msg.verb() + "'");
        }
    }

    /* ----- MessageRetargeter interface ---------------------------------- */

    /**
     * Find the object to handle a message for some class (either the object
     * itself or one of its mods).  This method is part of the message
     * handling subsystem; applications will not normally have need to call it.
     *
     * @param type  The class associated with the message verb.
     *
     * @return an object that can handle messages for class 'type', or null if
     *    this object doesn't handle messages for that class.
     */
    public DispatchTarget findActualTarget(Class type) {
        if (type == this.getClass()) {
            return this;
        } else if (myModSet == null) {
            return null;
        } else {
            return myModSet.getMod(type);
        }
    }

    /* ----- Referenceable interface --------------------------------------- */
    
    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     *    messages, either as the message target or as a parameter value.
     */
    public String ref() {
        return myRef;
    }
}
