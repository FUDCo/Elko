package org.elkoserver.util;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A hashtable-like collection that maps each key to a set of items rather than
 * to a single item.
 */
public class HashMapMulti<K, V> {
    /** Maps keys --> sets of values */
    private Map<K, HashSetMulti<V>> myMap;

    /** Version number, for detecting modification during iteration */
    private int myVersionNumber;

    /**
     * Construct an empty map.
     */
    public HashMapMulti() {
        myMap = new HashMap<K, HashSetMulti<V>>();
        myVersionNumber = 0;
    }

    /**
     * Add a value to a key's value set.
     *
     * @param key  The key for the value to add.
     * @param value  The value that should be added to 'key's value set
     */
    public void add(K key, V value) {
        HashSetMulti<V> set = myMap.get(key);
        if (set == null) {
            set = new HashSetMulti<V>();
            myMap.put(key, set);
        }
        set.add(value);
        ++myVersionNumber;
    }

    /**
     * Test if this map has a mapping for a given key.
     *
     * @param key  The key whose potential mapping is of interest.
     *
     * @return true if this map has one or more values for key, false if not.
     */
    public boolean containsKey(K key) {
        return myMap.containsKey(key);
    }

    /**
     * Return the set of values for some key.  Note that a set will always be
     * returned; if the given key has no values then the set returned will be
     * empty.
     *
     * @param key  The key for the set of values desired.
     *
     * @return a set of the values for 'key'.
     */
    public HashSetMulti<V> getMulti(K key) {
        HashSetMulti<V> result = myMap.get(key);
        if (result == null) {
            result = HashSetMulti.emptySet();
        }
        return result;
    }

    /**
     * Get the set of keys for this map.
     *
     * @return the keys for this map.
     */
    public Set<K> keys() {
        return myMap.keySet();
    }

    /**
     * Remove a value from a key's value set.
     *
     * @param key  The key for the value set to remove from.
     * @param value The value that should be removed from 'key's value
     *    set
     */
    public void remove(K key, V value) {
        HashSetMulti<V> set = myMap.get(key);
        if (set != null) {
            set.remove(value);
            if (set.isEmpty()) {
                myMap.remove(key);
            }
        }
        ++myVersionNumber;
    }

    /**
     * Remove a key's entire value set.
     *
     * @param key  The key for the set of values to remove.
     */
    public void remove(K key) {
        myMap.remove(key);
        ++myVersionNumber;
    }

    /**
     * Return an iterable that can iterate over all the values of all the keys
     * in this map.
     *
     * @return an iterable for the values in this map.
     */
    public Iterable<V> values() {
        return new Iterable<V>() {
            public Iterator<V> iterator() {
                return new HashMapMultiValueIterator();
            }
        };
    }

    private class HashMapMultiValueIterator implements Iterator<V> {
        private Iterator<HashSetMulti<V>> mySetIter;
        private Iterator<V> myValueIter;
        private V myNext;
        private int myStartVersionNumber;
        HashMapMultiValueIterator() {
            myStartVersionNumber = myVersionNumber;
            mySetIter = myMap.values().iterator();
            myValueIter = null;
            advance();
        }

        public boolean hasNext() {
            if (myVersionNumber != myStartVersionNumber) {
                throw new ConcurrentModificationException();
            }
            return myNext != null;
        }

        public V next() {
            if (myVersionNumber != myStartVersionNumber) {
                throw new ConcurrentModificationException();
            }
            V result = myNext;
            advance();
            return result;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            while (myValueIter == null || !myValueIter.hasNext()) {
                if (mySetIter.hasNext()) {
                    myValueIter = mySetIter.next().iterator();
                } else {
                    myNext = null;
                    return;
                }
            }
            myNext = myValueIter.next();
        }
    }
}
