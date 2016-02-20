package org.elkoserver.foundation.timer;

/**
 * Object which calls the {@link TickNoticer#noticeTick} method on a target
 * object every <i>n</i> milliseconds.  Clocks can only be created by calling
 * the {@link Timer#every every()} method on a {@link Timer} instance.<p>
 *
 * A <tt>Clock</tt> can be started and stopped.
 *
 * @see TickNoticer
 */
public class Clock extends TimerWatcher {
    
    /** The timer thread that is this clock's source of tick events */
    private TimerThread myThread;

    /** Target Object */
    private TickNoticer myTarget;

    /** Current run state */
    private boolean amTicking;

    /** Flag controlling synchronous tick notification */
    private boolean amSynchronous;

    /** Tick resolution in milliseconds */
    private long myResolution;

    /** Current tick number */
    private int myTicks;

    /**
     * Package private Constructor.  Called by the Timer.every() method.
     *
     * @param thread  The timer thread we are running with.
     * @param resolution  How often we are to get tick events.
     * @param arg  An arbitrary object to be passed along with the tick events.
     * @param synchronous  Flag controlling synchronous notification of clock
     *    ticks.  true=>notify synchronously; false=>post notification on
     *    message queue.
     */
    Clock(TimerThread thread, long resolution, TickNoticer target,
          boolean synchronous)
    {
        myThread = thread;
        myResolution = resolution;
        amSynchronous = synchronous;
        amTicking = false;
        myTicks = 0;
        myTarget = target;
    }

    /**
     * Gets the current tick number.  This is the number of times this clock
     * has ticked (i.e., invoked its {@link TickNoticer}) since it was created.
     *
     * @return the current tick count.
     */
    public int getTicks() {
        return myTicks;
    }

    /**
     * Called by the timer thread at clock tick time.
     */
    void handleTimeout() {
        if (amTicking) {
            ++myTicks;
            myTarget.noticeTick(myTicks);
        }
    }

    /**
     * Starts this clock from the current tick.
     */
    public synchronized void start() {
        if (!amTicking) {
            amTicking = true;
            myThread.setTimeout(true, myResolution, this);
        }
    }

    /**
     * Stops this clock from ticking.  It can be restarted with {@link #start}.
     */
    public void stop() {
        if (amTicking) {
            myThread.cancelTimeout(myEvent);
            myEvent = null;
            amTicking = false;
        }
    }
}
