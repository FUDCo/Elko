package org.elkoserver.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator for a collection of no elements.  This is sort of the Iterator
 * equivalent of a null pointer.
 */
public class EmptyIterator<V> implements Iterator<V> {

    /**
     * Constructor.
     */
    public EmptyIterator() {
    }

    /**
     * Returns true if the iteration has more elements.
     *
     * @return false (since, by definition, there are no elements).
     */
    public boolean hasNext() {
        return false;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration (actually, this will never
     *    happen; it will always throw).
     *
     * @throws NoSuchElementException  iteration has no more elements (always).
     */
    public V next() {
        throw new NoSuchElementException();
    }

    /**
     * Removes from the underlying collection the last element returned by the
     * iterator; will always throw an exception since there never was such an
     * element.
     *
     * @throws IllegalStateException since there are no elements in this
     *    collection.
     */
    public void remove() {
        throw new IllegalStateException();
    }
}
