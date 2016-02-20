package org.elkoserver.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over a single object.  This class is useful in the implementation
 * of collection-like interfaces in cases where there is no actual underlying
 * collection object.
 */
public class SingleObjectIterator<V> implements Iterator<V> {
    /** The object */
    private V myObject;

    /** Flag that iteration has yet to happen */
    private boolean amReady;

    /**
     * Constructor.
     *
     * @param object  The object to iterate over.
     */
    public SingleObjectIterator(V object) {
        myObject = object;
        amReady = true;
    }

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if {@link #next} would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        return amReady;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    public V next() {
        if (amReady) {
            amReady = false;
            return myObject;
        } else {
            throw new NoSuchElementException();
        }
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always.
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
