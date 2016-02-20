package org.elkoserver.server.context;

import org.elkoserver.foundation.json.Deliverer;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.Referenceable;
import org.elkoserver.util.EmptyIterator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Holder for the contents of a basic object that is a container.
 *
 * An object only acquires one of these if it actually contains stuff.
 */
class Contents implements Iterable<Item> {
    /** Marker object representing the "contents" of objects that are not
        allowed to be containers. */
    final static Contents theVoidContents = new Contents(null);

    /** The contents themselves, stored in the order added. */
    private List<Item> myContents;

    /**
     * Internal constructor.
     */
    private Contents(List<Item> contents) {
        myContents = contents;
    }

    /**
     * Constructor.  This is private so it won't be called externally.  The
     * proper API for other objects to use is the 'withContents' static method.
     */
    private Contents() {
        this(new LinkedList<Item>());
    }

    /**
     * Add an item to these contents.
     *
     * @param item  The item to add.
     */
    private void add(Item item) {
        myContents.add(item);
    }

    /**
     * Encode these contents as a JSONLiteralArray object.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSONLiteralArray object representing these contents.
     */
    JSONLiteralArray encode(EncodeControl control) {
        JSONLiteralArray result = new JSONLiteralArray(control);
        if (myContents != null) {
            for (Item elem : myContents) {
                if (control.toClient()) {
                    result.addElement(elem.ref());
                } else if (!elem.isEphemeral()) {
                    result.addElement(elem.baseRef());
                }
            }
        }
        result.finish();
        return result;
    }

    /**
     * Get an iterator over these contents.
     *
     * @return an iterator over the items contained by this contents object.
     */
    public Iterator<Item> iterator() {
        if (myContents == null) {
            return new EmptyIterator<Item>();
        } else {
            return myContents.iterator();
        }
    }

    /**
     * Remove an item from these contents.
     *
     * @param item  The item to remove.
     *
     * @return true if the contents became empty as a result.
     */
    private boolean remove(Item item) {
        myContents.remove(item);
        return myContents.isEmpty();
    }

    /**
     * Transmit a description of these contents as a series of 'make' messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     */
    private void sendContentsDescription(Deliverer to, Referenceable maker) {
        if (myContents != null) {
            for (Item elem : myContents) {
                elem.sendItemDescription(to, maker, false);
            }
        }
    }

    /**
     * Transmit contents as a series of 'make' messages.
     *
     * @param to  Where to send the description.
     * @param maker  Maker object to address message to.
     * @param contents  The contents to transmit, if not null.
     */
    static void sendContentsDescription(Deliverer to, Referenceable maker,
                                        Contents contents)
    {
        if (contents != null) {
            contents.sendContentsDescription(to, maker);
        }
    }

    /**
     * Add an item to a contents container, creating the container if
     * necessary to do so.
     *
     * @param contents  The contents to which the item is to be added, or null
     *    if no contents yet exist.
     * @param item  The item to add.
     *
     * @return the contents (created if necessary), with 'item' in it.
     */
    static Contents withContents(Contents contents, Item item) {
        if (contents == null) {
            contents = new Contents();
        }
        contents.add(item);
        return contents;
    }

    /**
     * Remove an item from a contents container.  It is not an error for the
     * item to not be in the container (or even for the container to not
     * exist).
     *
     * @param contents  The contents from which the item is to be removed.
     * @param item  The item to remove.
     *
     * @return the contents, without 'item' in it.
     */
    static Contents withoutContents(Contents contents, Item item) {
        if (contents == null) {
            return null;
        } else {
            if (contents.remove(item)) {
                return null;
            } else {
                return contents;
            }
        }
    }
}
