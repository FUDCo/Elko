package org.elkoserver.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over an array of objects.  Usable in iterator-based for loops when
 * the underlying array is null, and when you actually need an explicit
 * iterator.
 */
public class ArrayIterator<V> implements Iterator<V> {
    /** The array */
    private V[] myArray;

    /** Iteration position */
    private int myIndex;

    /**
     * Constructor.
     *
     * @param array  The array to iterate over.
     */
    public ArrayIterator(V[] array) {
        myArray = array;
        myIndex = 0;
    }

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if {@link #next} would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        return myArray != null && myIndex < myArray.length;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    public V next() {
        if (hasNext()) {
            return myArray[myIndex++];
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
