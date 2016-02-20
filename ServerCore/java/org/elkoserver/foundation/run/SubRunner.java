package org.elkoserver.foundation.run;

import java.util.concurrent.Callable;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Object which manages a Runner for you.  It accepts tasks to do, which it
 * processes on its own asynchronous run queue, and handlers for the results,
 * which it processes on your run queue.
 */
public class SubRunner {
    /** Asynch run queue for giving tasks to the sub-runner thread */
    private Runner myRunner;

    /** Asynch run queue for giving results back to the parent thread */
    private Runner myReturnRunner;

    /**
     * Start a new sub-runner.
     */
    public SubRunner() {
        this("Elko Sub-RunQueue");
    }

    /**
     * Start a new, named sub-runner.
     */
    public SubRunner(String name) {
        myReturnRunner = Runner.currentRunner();
        myRunner = new Runner(name);
    }

    /**
     * Enqueue a task for execution.
     *
     * @param task  Callable that will perform the desired task; runs in this
     *    SubRunner's run queue.
     * @param resultee  ResultThank that will be given the result from running
     *    'task'; runs in the run queue of the thread that created this
     *    SubRunner.
     */
    public void enqueue(Callable<Object> task, ArgRunnable resultee) {
        myRunner.enqueue(new CallReturnThunk(task, resultee));
    }

    /**
     * Shut down this sub-runner's run queue.
     */
    public void shutdown() {
        myRunner.orderlyShutdown();
    }

    /**
     * Run a task in one thread and then return the result to another thread.
     */
    private class CallReturnThunk implements Runnable {
        private Callable<Object> myTask;
        private ArgRunnable myResultee;
        private Object myValue;
        CallReturnThunk(Callable<Object> task, ArgRunnable resultee) {
            myTask = task;
            myResultee = resultee;
            myValue = null;
        }
        public void run() {
            if (myTask != null) {
                try {
                    myValue = myTask.call();
                    myTask = null;
                    if (myResultee != null) {
                        myReturnRunner.enqueue(this);
                    }
                } catch (Throwable t) {
                    Trace.runq.errorm("problem in sub-runner", t);
                }
            } else {
                myResultee.run(myValue);
            }
        }
    }
}
