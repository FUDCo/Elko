package org.elkoserver.foundation.run;

import org.elkoserver.util.trace.Trace;
import java.util.concurrent.Callable;

/**
 * Runs when it can, but never on empty.  A thread services a queue
 * of Runnables.
 */
public class Runner implements Runnable {

    static private Trace tr = Trace.trace("runner");

    /**
     * Number of Runners currently in operation.  When this goes to 0, it is
     * time to exit.
     */
    static private int theRunnerCount = 0;

    /**
     * DANGER DANGER: Mutable static.
     */
    static private Runner theDefaultRunner = null;

    /**
     * The number of Runnables to dequeue and run in one go.
     * Must be >= 1.
     */
    static private final int DEQUEUE_GRANULARITY = 25;

    /**
     * Note that Queue is a thread-safe data structure with its own lock.
     */
    private Queue myQ;

    /**
     * If we ever go orthogonal again, myThread must not be
     * checkpointed.  Ie, it must be a DISALLOWED_FIELD or 'transient'
     * or something.
     */
    private RunnerThread myThread;

    /**
     * Normally, myWorker == myThread.
     * While another thread is synchronously calling into this runner and
     * holding myRunLock, that thread is remembered in myWorker so it
     * can be interrupted instead of myThread.
     */
    private Thread myWorker;

    /**
     * The lock protecting the heap of objects "inside" the runner.  All
     * application execution inside the server must only happen while this
     * lock is held.
     */
    private Object myRunLock;

    /**
     * When the queue is empty, myThread blocks on myNotifyLock rather
     * than myRunLock to avoid a peculiar deadlock possibility: In
     * java, one cannot notify() a lock without grabbing it, and
     * enqueue() needs to notify the lock that myThread is wait()ing
     * on.  This should be fine, as enqueue() only notify()s when
     * myThread is waiting, and therefore not holding the runLock.
     * However, a now() may be holding the runLock.  If that now()
     * blocks waiting on a lock held by enqueue()'s caller, we
     * deadlock.  It would be too hard to explain or remember what not
     * to do to avoid this deadlock.  Hence a separate lock.  Ugh.
     */
    private Object myNotifyLock;
    private boolean myNeedsNotify = false;
    
    /**
     * Has an orderly shutdown been requested?
     */
    private boolean myIsShuttingDown = false;
    
    /**
     * Makes a Runner, and starts the thread that services its queue.
     * The name of the thread will be "Elko RunQueue".
     */
    public Runner() {
        this("Elko RunQueue");
    }

    /**
     * Makes a Runner, and starts the thread that services its queue.
     *
     * @param name is the name to give to the thread created.
     */
    public Runner(String name) {
        ++theRunnerCount;
        myRunLock = new Object();
        myNotifyLock = new Object();
        myQ = new Queue(Runnable.class);
        myWorker = myThread = new RunnerThread(this, name);
        myThread.start();
    }
    
    /**
     * If called from within a thread servicing a Runner, returns that
     * Runner.  Otherwise, returns the default Runner.
     */
    static public Runner currentRunner() {
        Thread t = Thread.currentThread();
        if (t instanceof RunnerThread) {
            return (Runner) ((RunnerThread) t).myRunnable;
        } else {
            if (theDefaultRunner == null) {
                theDefaultRunner = new Runner();
            }
            return theDefaultRunner;
        }
    }

    /**
     * Utility routine to either swallow or throw exceptions, depending on
     * whether or not they are the kind of exceptions that need to escape from
     * the run loop.
     */
    static public void throwIfMandatory(Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof LinkageError) {
            throw (LinkageError) t;
        }
    }

    /**
     * Queues something for this Runnable's thread to do.  May be called
     * from any thead.
     */
    public void enqueue(Runnable todo) {
        /* enqueueing is guarded by the queue's lock, not the runLock. */
        myQ.enqueue(todo);
        if (myNeedsNotify) {
            /* even here, enqueue() avoids grabbing the runLock. */
            synchronized (myNotifyLock) {
                myNotifyLock.notify();
            }
        }
    }

    /**
     * Tests whether the current thread is holding the run lock
     */
    public boolean isCurrentThreadInRunner() {
        return Thread.currentThread() == myWorker;
    }

    /**
     * Schedules a thunk to execute "inside" this runner (in the RunnerThread
     * as a separate turn while holding the runLock), while also effectively 
     * executing as a synchronous call within the requestors's thread.
     * 
     * In most ways this can be thought of as a symmetric rendezvous between 
     * the two Threads.  The reason we *specify* that the thunk is executed
     * specifically in the requested runner's RunnerThread is so that 
     * thread-scoped state, such as Runner.currentRunner(), will be according 
     * to the Runner receiving the now() request, not whatever thread made 
     * the request.
     */
    public Object now(Callable<Object> todo) {
        NowRunnable nr = new NowRunnable(todo);
        enqueue(nr);
        return nr.runNow();
    }

    /**
     * Will enqueue a request to shut down this runner's thread.  Since
     * this is an enqueued request, the thread will only shut down
     * after finishing earlier requests, as well as any now()s that
     * happen in the meantime.
     */
    public void orderlyShutdown() {
        enqueue(new ShutdownRunQException());
        myIsShuttingDown = true;
    }
    
    /**
     * Tests if an orderlyShutdown been requested.  Note that messages already 
     * enqueued will still be serviced before the shutdown request is 
     * honored.
     *
     * @return true if the Runner is shutting down
     */
    public boolean isShuttingDown() {
        return myIsShuttingDown;
    }

    /**
     * Called only by {@link Thread#start}.  Pulls Runnables off of the queue
     * until there aren't any more, then waits until there's more to do.
     */
    public void run() {
        int msgCount = 0;
        for (;;) {
            try {
                Thread.yield();
                Runnable todo = null;
                synchronized (myRunLock) {
                    for (int i = 0; i < DEQUEUE_GRANULARITY; ++i) {
                        /* This call to optDequeue() will momentarily acquire
                           the queue lock while we're still holding the
                           runLock!  I believe this is safe under all
                           conditions, but cannot prove it at this time. */
                        todo = (Runnable) myQ.optDequeue();
                        if (todo == null) {
                            break;
                        }
                        todo.run();
                        ++msgCount;
                    }
                }
                if (todo != null) {
                    continue;
                }
                /* We can't sleep by waiting on the runLock (see myNotifyLock)
                   but we have to release the runLock while we're asleep, so
                   our sleepage logic is here, outside the above synchronized
                   block. */
                synchronized(myNotifyLock) {
                    /* More elements could have arrived in the meantime, so
                       check again after grabbing myNotifyLock. */
                    if (myQ.hasMoreElements()) {
                        continue;
                    }
                    if (tr.debug && Trace.ON) {
                        tr.debugm
                          ("RunQ empty after " + msgCount +
                           " messages.  sleeping now.");
                    }
                    msgCount = 0;
                    myNeedsNotify = true;
                    try {
                        waitForMore();
                    } catch (InterruptedException e) {
                        /* ignore */
                    } finally {
                        myNeedsNotify = false;
                    }
                }
            } catch (ShutdownRunQException sdve) {
                /* This kludge is the least painful way I could think of to do
                   an orderly shutdown of a runner thread without imposing
                   *any* extra overhead on the normal case. */
                --theRunnerCount;
                if (theRunnerCount == 0) {
                    System.exit(0);
                }
                return;
            } catch (Throwable t) {
                if (tr.error) {
                    tr.errorReportException(t,
                        "Exception made it all the way out of the run " +
                        "loop.  Restarting it.");
                }
            }
        }
    }

    /**
     * Called by run() when we're out of messages in the queue, or
     * after the debug hook does its biz.  Must only while
     * myNotifyLock is held. 
     *
     * May be overridden in a subclass to wake up occasionally to do
     * background things, like checking for finalizers.  The implementation
     * here simply wait()s on myNotifyLock.
     */
    void waitForMore() throws InterruptedException {
        myNotifyLock.wait();
    }
}
