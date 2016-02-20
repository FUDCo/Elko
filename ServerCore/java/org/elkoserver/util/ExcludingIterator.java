package org.elkoserver.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over a collection that excludes a distinguished element.
 */
abstract public class ExcludingIterator<V> implements Iterator<V> {
    /** The base iterator */
    private Iterator<V> myBase;

    /** Single element lookahead, so we can tell if we're done. */
    private V myLookahead;

    /**
     * Constructor.
     *
     * @param base  The underlying iterator.
     * @param exclusion  The distinguished element to exclude.
     */
    public ExcludingIterator(Iterator<V> base) {
        myBase = base;
        skipExcludedElements();
    }

    private void skipExcludedElements() {
        myLookahead = null;
        while (myBase.hasNext()) {
            V elem = myBase.next();
            if (!isExcluded(elem)) {
                myLookahead = elem;
                break;
            }
        }
    }

    /**
     * Test if a given element should be excluded from the iteration.
     * Sub-classes implement this.
     *
     * @param element  The element to be tested.
     *
     * @return true if the element should be excluded from the iteration,
     *   false if if should be included.
     */
    abstract public boolean isExcluded(V element);

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if {@link #next} would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        return myLookahead != null;
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
            V result = myLookahead;
            skipExcludedElements();
            return result;
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
