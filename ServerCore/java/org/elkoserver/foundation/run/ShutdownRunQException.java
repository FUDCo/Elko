package org.elkoserver.foundation.run;

/**
 * This peculiar exception is also a Runnable that when run() throws
 * itself.  It exists as part of the implementation of Runner to
 * enable it to do a no-overhead orderly shutdown.
 */
class ShutdownRunQException extends RuntimeException implements Runnable {

    public ShutdownRunQException() {}
    public ShutdownRunQException(String m) { super(m); }

    /**
     *
     */
    public void run() {
        throw this;
    }
}
