package org.elkoserver.util;

/**
 * Arbitrary executable to be run on an argument.
 *
 * This interface is analagous to {@link java.lang.Runnable}, but takes a
 * parameter.
 */
public interface ArgRunnable {
    /**
     * Act upon a value.
     *
     * @param obj  The value to act upon.
     */
    void run(Object obj);
}
