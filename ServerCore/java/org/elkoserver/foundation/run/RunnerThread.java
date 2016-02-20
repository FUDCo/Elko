package org.elkoserver.foundation.run;

/**
 * Makes a Runnable into a Thread-scoped variable.
 */
class RunnerThread extends Thread {
    Runnable myRunnable;

    RunnerThread(Runnable runnable, String name) {
        super(runnable, name);
        myRunnable = runnable;
    }
}
