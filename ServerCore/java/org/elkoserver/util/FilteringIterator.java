package org.elkoserver.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator that takes an iterator producing values of one type and generates
 * values of another type via a transformation method that is supplied by the
 * implementing subclass.
 */
public class FilteringIterator<From,To> implements Iterator<To> {
    /** The base iterator */
    private Iterator<From> myBase;

    /** Filter that transforms objects from one class to another. */
    private Filter<From, To> myFilter;

    /**
     * Constructor.
     *
     * @param base  The iterator whose elements are to be transformed.
     * @param filter  The filter to transform the iterata.
     */
    public FilteringIterator(Iterator<From> base, Filter<From, To> filter) {
        myBase = base;
        myFilter = filter;
    }

    /**
     * Returns true if the iteration has more elements.  (In other words,
     * returns true if {@link #next} would return an element rather than
     * throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        return myBase.hasNext();
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     *
     * @throws NoSuchElementException  iteration has no more elements.
     */
    public To next() {
        return myFilter.transform(myBase.next());
    }

    /**
     * This operation is not supported.
     *
     * @throws UnsupportedOperationException always.
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public static interface Filter<From, To> {
        /**
         * Generate an object of type To given an object of type From.
         *
         * @param from  The object to be transformed.
         *
         * @return the object 'from' transforms into.
         */
        To transform(From from);
    }
}
