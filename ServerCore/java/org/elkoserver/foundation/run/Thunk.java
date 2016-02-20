package org.elkoserver.foundation.run;

/**
 * An arbitrary (zero-argument) executable.  Similar to java.lang.Runnable but
 * can throw an exception.
 *
 * @see java.lang.Runnable
 */
public interface Thunk {
    /**
     * Execute this thunk.
     */
    Object run() throws Throwable;
}
