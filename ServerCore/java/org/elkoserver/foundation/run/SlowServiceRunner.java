package org.elkoserver.foundation.run;

import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.elkoserver.util.ArgRunnable;

/**
 * This class provides a mechanism for safely making use of external services
 * that are only available via synchronous, blocking (i.e., slow) interfaces.
 * It maintains a thread pool in which calls to such services run, delivering
 * their results back via callback thunks that are dropped onto the normal
 * server run queue.
 */
public class SlowServiceRunner {
    /** Asynch run queue for giving results back to the main thread. */
    private Runner myResultRunner;

    /** Executor to dole out work to a pool of threads that it manages. */
    private ThreadPoolExecutor myExecutor;

    /**
     * Constructor.  Note that while this is a constructable class, in practice
     * it should usually be managed as a singleton, i.e., don't mak more than
     * one.
     *
     * @param resultRunner  Run queue in which result handlers will be run
     * @param maxPoolSize  Maximum number of threads allowed in the thread pool
     */
    public SlowServiceRunner(Runner resultRunner, int maxPoolSize) {
        myResultRunner = resultRunner;
        myExecutor =
            new ThreadPoolExecutor(1, maxPoolSize, 60, TimeUnit.SECONDS,
                                   new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Enqueue a task to be executed via the slow path.  Unlike most code
     * running in our environment, tasks run via this mechanism *are* allowed
     * to invoke operations that can block.  However, they are not allowed to
     * lock or reference any normal mutable server state.  Since there is not
     * really any graceful way to enforce the latter rule, tasks are on their
     * honor to be well behaved!<p>
     *
     * Tasks should return a result object that will be passed to the given
     * result handler for execution in the main server run queue.  It is
     * permissible for a task result to be null.  However, if a task throws an
     * exception, this will be given to the handler as the result, so if the
     * normal task result is an exception type, it is up to the programmer of
     * the task and the result handler to take measures to sort things out
     * appropriately.
     *
     * @param task  Callable that executes the task.  This will be executed in
     *    a separate thread.
     * @param resultHandler  Thunk that will be invoked with the result
     *    returned by the task.  This will be executed on the main run queue.
     */
    public void enqueueTask(final Callable<Object> task,
                            final ArgRunnable resultHandler)
    {
        myExecutor.execute(new Runnable() {
            public void run() {
                Object realResult;
                try {
                    realResult = task.call();
                } catch (Exception e) {
                    realResult = e;
                }
                final Object result = realResult;
                myResultRunner.enqueue(new Runnable() {
                    public void run() {
                        if (resultHandler != null) {
                            resultHandler.run(result);
                        }
                    }
                });
            }
        });
    }
}
