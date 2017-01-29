package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.trace.Trace;
import java.util.LinkedList;
import java.util.List;

/**
 * A Item is an application object contained by a context or a user (or another
 * Item) but which is not a context or user itself.  Along with {@link Context}
 * and {@link User} it is one of the three basic object types.
 */
public class Item extends BasicObject {
    /** Flag that users may delete this item. */
    private boolean amDeletable;

    /** Flag that users may move this item around. */
    private boolean amPortable;

    /* Fields below here only apply to active items. */

    /** Object that contains this item. */
    private BasicObject myContainer;

    /** Optional watcher for container updates. */
    private ContainerWatcher myContainerWatcher;

    /**
     * Manual item constructor.  Items constructed via this method are created
     * with no mods and no contents.
     *
     * @param name  Item name.
     * @param isContainer  Flag indicating whether the item may be used as a
     *    container.
     * @param isDeletable  Flag indicating whether users my delete this item.
     * @param pos  Optional position of the item within its container.
     */
    Item(String name, boolean isContainer, boolean isDeletable, Position pos) {
        super(name, null, isContainer, null, pos);
        amDeletable = isDeletable;
        myContainerWatcher = null;
    }

    /**
     * JSON-driven constructor.
     *
     * @param name  The name of the item.
     * @param ref  Nominal reference string for the item (may be overridden).
     * @param mods  Array of mods to attach to the user; can be null if no mods
     *    are to be attached at initial creation time.
     * @param contents  Array of inactive items that will be the initial
     *    contents of this user, or null if there are no contents now.
     * @param in  Optional ref of container holding this item.
     * @param isPossibleContainer  Flag indicating whether the item may be used
     *    as a container.
     * @param isDeletable  Flag indicating whether users may delete this item.
     * @param isPortable  Flag indicating whether users may move this item.
     * @param pos  Optional position of the item within its container.
     */
    @JSONMethod({ "name", "ref", "mods", "contents", "in", "cont", "deletable",
                  "portable", "?pos" })
    Item(String name, OptString ref, Mod mods[], Item contents[], OptString in,
         OptBoolean isPossibleContainer, OptBoolean isDeletable,
         OptBoolean isPortable, Position pos)
    {
        super(name, mods, isPossibleContainer.value(true), contents, pos);
        myRef = ref.value(null);
        amDeletable = isDeletable.value(false);
        amPortable = isPortable.value(false);
        myContainerWatcher = null;
    }

    /**
     * Add a new mod to this item.  The mod must be an {@link ItemMod} even
     * though the method is declared generically.  If it is not, it will not be
     * added, and an error message will be written to the log.
     *
     * @param mod  The mod to attach; must be an {@link ItemMod}.
     */
    void attachMod(Mod mod) {
        if (mod instanceof ItemMod) {
            super.attachMod(mod);
        } else {
            context().trace().errorm(
                "attempt to attach non-ItemMod " + mod + " to " + this);
        }
        if (mod instanceof ContainerWatcher) {
            if (myContainerWatcher == null) {
                myContainerWatcher = (ContainerWatcher) mod;
            } else {
                context().trace().errorm("ContainerWatcher mod " + mod +
                    " added to " + this + ", which already has one");
            }
        }
    }

    /**
     * Obtain this item's container.
     *
     * @return the object this item is currently contained by.
     */
    public BasicObject container() {
        return myContainer;
    }

    /**
     * Obtain the context in which this item is located, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the context in which this item is located, at whatever level of
     *    container nesting, or null if it is not in any context.
     */
    public Context context() {
        if (myContainer == null) {
            return null;
        } else {
            return myContainer.context();
        }
    }

    /**
     * Delete this item (and, by implication, its contents).  The caller is
     * responsible for notifying any clients who need to know that this has
     * happened.
     */
    public void delete() {
        /* copy contents list to avoid concurrent modification problems */
        List<Item> copy = new LinkedList<Item>();
        for (Item item : contents()) {
            copy.add(item);
        }
        for (Item item : copy) {
            item.delete();
        }
        setContainer(null);
        markAsDeleted();
        myContextor.remove(this);
    }

    protected void baseEncode(JSONLiteral result, EncodeControl control) {
        result.addParameter("ref", myRef);
        result.addParameterOpt("name", myName);
        result.addParameterOpt("pos", myPosition);
        if (myModSet != null) {
            JSONLiteralArray mods = myModSet.encode(control);
            if (mods.size() > 0) {
                result.addParameter("mods", mods);
            }
        }
        if (!isContainer()) {
            result.addParameter("cont", false);
        }
        if (amPortable) {
            result.addParameter("portable", true);
        }
        if (control.toRepository()) {
            if (myContainer != null) {
                result.addParameter("in", myContainer.baseRef());
            }
            if (amDeletable) {
                result.addParameter("deletable", true);
            }
            /*
            if (myContents != null) {
                JSONLiteralArray contentsArray = myContents.encode(control);
                if (contentsArray.size() > 0) {
                    result.addParameterRef("contents", contentsArray);
                }
            }
            */
        }
    }

    /**
     * Encode this item for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this item.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("item", control);
        baseEncode(result, control);
        result.finish();
        return result;
    }

    /**
     * Obtain the user or context holding this object, regardless of how deeply
     * nested in containers it might be.
     *
     * @return the user or context within which this object is contained, at
     *    whatever level of container nesting, or null if it is not held by
     *    anything.
     */
    public BasicObject holder() {
        if (myContainer == null) {
            return null;
        } else if (myContainer instanceof Item) {
            return myContainer.holder();
        } else {
            return myContainer;
        }
    }

    /**
     * Test if unprivileged users inside the context can delete this item (by
     * sending a 'delete' message to the server).
     *
     * @return  true if ordinary users can delete this item, false if not.
     */
    public boolean isDeletable() {
        return amDeletable;
    }

    /**
     * Test if unprivileged users inside the context can move this item (by
     * sending a 'move' message to the server).
     *
     * @return  true if ordinary users can move this item, false if not.
     */
    public boolean isPortable() {
        return amPortable;
    }

    /**
     * Transmit a description of this item as a series of 'make' messages.
     *
     * <p>This method will generate and send a series of 'make' messages that
     * direct a client or clients to construct a representation of this item
     * and its contents.  If this item is visible to the indicated receiver,
     * one 'make' message will be sent describing this object itself, and then
     * an additional message for each visible object in its descendent
     * containership hierarchy.
     *
     * <p>Application code will not normally need to call this method, since it
     * is invoked automatically as part of the normal transmission of a context
     * and its contents to users arriving in the context.  However, certain
     * specialized applications that synthesize objects directly will need to
     * call this in order to describe what they have created to connected
     * clients.
     *
     * @param to  Where to send the description.  This is the destination to
     *    which these messages will be delivered; it will typically be a
     *    context or user object.  It is the entity with respect to which this
     *    item's visibility or invisibility (and that of its contents) is
     *    determined for purposes of deciding whether or not to send 'make'
     *    messages.
     * @param maker  Maker object to address message to.  This is the object
     *    that is responsible, on the client, for creating the client presence
     *    of the item.  Normally this should be the item's container.
     * @param force  If true, force the transmission, even if this item is
     *    marked as being invisible to 'to'.  Note, however, that forcing
     *    transmission in this manner only overrides the invisibility of this
     *    item itself and not that of any other items that it may contain.
     */
    void sendItemDescription(Deliverer to, Referenceable maker, boolean force)
    {
        if (force || visibleTo(to)) {
            to.send(Msg.msgMake(maker, this));
            Contents.sendContentsDescription(to, this, myContents);
        }
    }

    /**
     * Transmit a description of this item as a series of 'make' messages,
     * such that the receiver will be able to construct a local presence of it.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address the message(s) to.
     */
    public void sendObjectDescription(Deliverer to, Referenceable maker) {
        sendItemDescription(to, maker, false);
    }

    /**
     * Set or change this item's container.  The participating containers will
     * be marked as having changed, so that the change of containership will be
     * persistent.
     *
     * @param container  The new container for this item, or null to indicate
     *    that it should now have no container.
     */
    public void setContainer(BasicObject container) {
        BasicObject oldContainer = myContainer;
        if (myContainer != null) {
            myContainer.markAsChanged();
            myContainer.noteCodependent(this);
            noteCodependent(myContainer);
            if (container != null) {
                myContainer.noteCodependent(container);
                container.noteCodependent(myContainer);
            }
        }
        setContainerPrim(container);
        if (myContainer != null) {
            myContainer.markAsChanged();
            myContainer.noteCodependent(this);
            noteCodependent(myContainer);
        }
        if (myContainerWatcher != null) {
            myContainerWatcher.noteContainerChange(oldContainer, container);
        }
    }
    
    /**
     * Set or change this item's container primitively.  This routine does not
     * fiddle with the changed flags and is for use during object construction
     * only.
     *
     * @param container  The new container for this item, or null to indicate
     *    that it should now have no container.
     */
    void setContainerPrim(BasicObject container) {
        if (myContainer != null) {
            myContainer.removeFromContents(this);
        }
        myContainer = container;
        if (myContainer != null) {
            myContainer.addToContents(this);
        }
    }

    /**
     * Obtain a printable string representation of this item.
     *
     * @return a printable representation of this item.
     */
    public String toString() {
        return "Item '" + myRef + "'";
    }

    /**
     * Return the proper type tag for this object.
     *
     * @return a type tag string for this kind of object; in this case, "item".
     */
    String type() {
        return "item";
    }

    /**
     * Obtain the user within which this item is contained, regardless of how
     * deeply nested in containers it might be.
     *
     * @return the user in which this item is contained, at whatever level of
     *    container nesting, or null if it is not contained by a user.
     */
    public User user() {
        if (myContainer == null) {
            return null;
        } else {
            return myContainer.user();
        }
    }

    /**
     * Message handler for the 'delete' message.
     *
     * <p>If the item is deletable, the item is deleted and a 'delete' message
     * is broadcast to everyone in the context informing them of this.
     */
    @JSONMethod
    public void delete(User from) throws MessageHandlerException {
        ensureSameContext(from);
        if (amDeletable) {
            from.context().send(Msg.msgDelete(this));
            delete();
        }
    }
}
