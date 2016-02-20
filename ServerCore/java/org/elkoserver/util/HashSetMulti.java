package org.elkoserver.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A hash "set" that objects can be added to multiple times.  An object must be
 * removed an equal number of times before it disappears.
 *
 * This class is to {@link HashMapMulti} as {@link java.util.HashSet HashSet}
 * is to {@link HashMap}, but note that it does not truly implement the set
 * abstraction since the number of times a value is entered is significant.
 */
public class HashSetMulti<V> implements Iterable<V> {
    /** Maps objects --> counts of membership */
    private Map<V, Integer> myMembers;

    /** Flag blocking modification, to implement a read-only view. */
    private boolean amReadOnly;

    /**
     * Construct a new, empty set.
     */
    public HashSetMulti() {
        myMembers = new HashMap<V, Integer>();
        amReadOnly = false;
    }

    /**
     * Private constructor for creating read-only sets.
     */
    private HashSetMulti(Map<V, Integer> map) {
        myMembers = map;
        amReadOnly = true;
    }

    /**
     * Add an object to the set.
     *
     * @param obj  The object to add.
     */
    public void add(V obj) {
        if (amReadOnly) {
            throw new UnsupportedOperationException("read-only set");
        }
        Integer count = myMembers.get(obj);
        if (count == null) {
            count = new Integer(1);
        } else {
            count = count + 1;
        }
        myMembers.put(obj, count);
    }

    /**
     * Produce a new set that is a read-only version of this one.
     *
     * @return an unmodifiable view onto this set.
     */
    public HashSetMulti<V> asUnmodifiable() {
        return new HashSetMulti<V>(myMembers);
    }

    /**
     * Test if a given object is a member of the set (i.e., that it has been
     * added more times than it has been removed).
     *
     * @param obj  The object to test for.
     *
     * @return true if 'obj' is a member of this set.
     */
    public boolean contains(V obj) {
        if (myMembers != null) {
            return myMembers.containsKey(obj);
        } else {
            return false;
        }
    }

    /**
     * Produce an empty set.
     *
     * @return a read-only empty set.
     */
    public static <V> HashSetMulti<V> emptySet() {
        return new HashSetMulti<V>(null);
    }

    /**
     * Test if this set is empty.
     *
     * @return true if this set contains no members.
     */
    public boolean isEmpty() {
        return myMembers == null || myMembers.isEmpty();
    }

    /**
     * Obtain an iterator over the objects in this set (not repeating
     * multiples).
     *
     * @return an iterator over the objects contained by this set.  Note that
     *    each object that is in the set is returned exactly once, regardless
     *    of how many times it has been added.
     */
    public Iterator<V> iterator() {
        if (myMembers != null) {
            return myMembers.keySet().iterator();
        } else {
            return new EmptyIterator<V>();
        }
    }

    /**
     * Remove an object from the set.  Note that the object only really
     * disappears from the set once it has been removed as many times as it
     * was added.  Removes in excess of adds are silently ignored.
     *
     * @param obj  The object to remove.
     */
    public void remove(V obj) {
        if (amReadOnly) {
            throw new UnsupportedOperationException("read-only set");
        }
        Integer count = myMembers.get(obj);
        if (count != null) {
            int newCount = count - 1;
            if (newCount == 0) {
                myMembers.remove(obj);
            } else {
                myMembers.put(obj, newCount);
            }
        }
    }
}
