package org.elkoserver.foundation.timer;

/**
 * The master control object for scheduling timed events using timeouts and
 * clocks.  One-time events (controlled by {@link Timeout} objects) may be
 * scheduled by calling either of the {@link #after after()} methods.
 * Recurring events (controlled by {@link Clock} objects) may be scheduled by
 * calling either of the {@link #every every()} methods.<p>
 *
 * Event notification is guaranteed to be prompt but not immediate: the event
 * handler will be invoked no sooner than scheduled and as soon thereafter as
 * possible, but no guarantees are offered that somewhat more time will not
 * have passed than was requested.  In particular, while the scheduling API
 * lets you specify times with millisecond precision, millisecond accuracy in
 * practice should not be assumed.
 */
public class Timer {
    
    /** The single permitted instance of this class */
    static private Timer theTimer = new Timer();

    /** The timer thread */
    private TimerThread myThread = null;

    /**
     * Private constructor.  Just start the timer thread.
     */
    private Timer() {
        myThread = new TimerThread();
        myThread.start();
    }

    /**
     * Sets a timeout for the specified number of milliseconds.  After the
     * timer expires, <tt>target</tt>'s {@link TimeoutNoticer#noticeTimeout
     * noticeTimeout()} method is called.
     *
     * @param millis  How long to wait until timing out.
     * @param target  Object to be informed when the time comes.
     * @param synchronous  Flag controlling synchronous notification of the
     *    timeout.  If <tt>true</tt>, notify synchronously; if <tt>false</tt>,
     *    post notification on the message queue.  Note that great care should
     *    be taken with synchronous notification as it introduces threading
     *    issues; do not use synchronous notification unless you understand
     *    these issues thoroughly (and, by the way, you don't).
     *
     * @return a timeout object that can be used to cancel or identify the
     *   timeout.
     *
     * @see TimeoutNoticer
     */
    public Timeout after(long millis, TimeoutNoticer target,
                         boolean synchronous)
    {
        Timeout newTimeout = new Timeout(myThread, target, synchronous);
        myThread.setTimeout(false, millis, newTimeout);
        return newTimeout;
    }

    /**
     * Sets a timeout for the specified number of milliseconds.  After the
     * timer expires, <tt>target</tt>'s {@link TimeoutNoticer#noticeTimeout
     * noticeTimeout()} method is called.  Notification is always asynchronous:
     * this method is equivalent to the {@link
     * #after(long,TimeoutNoticer,boolean)} method where the
     * <tt>synchronous</tt> argument is set to <tt>false</tt>.
     *
     * @param millis  How long to wait until timing out.
     * @param target  Object to be informed when the time comes.
     *
     * @return a timeout object that can be used to cancel or identify the
     *   timeout.
     *
     * @see TimeoutNoticer
     */
    public Timeout after(long millis, TimeoutNoticer target) {
        return after(millis, target, false);
    }

    /**
     * Creates a new clock.  The new clock begins life stopped with its tick
     * count at zero (start the clock ticking by calling its {@link Clock#start
     * start()} method).
     *
     * @param resolution  The clock tick interval.
     * @param target  Object to be sent tick notifications.
     * @param synchronous Flag controlling synchronous notification of clock
     *    ticks.  If <tt>true</tt>, notify synchronously; if <tt>false</tt>,
     *    post notification on the message queue.  Note that great care should
     *    be taken with synchronous notification as it introduces threading
     *    issues; do not use synchronous notification unless you understand
     *    these issues thoroughly.
     *
     * @return a new clock object according to the given parameters.
     *
     * @see TickNoticer
     */
    public Clock every(long resolution, TickNoticer target,
                       boolean synchronous)
    {
        return new Clock(myThread, resolution, target, synchronous);
    }

    /**
     * Creates a new clock.  The new clock begins life stopped with its tick
     * count at zero (start the clock ticking by calling its {@link Clock#start
     * start()} method).  Clock ticks are always asynchronous: this method is
     * equivalent to the {@link #every(long,TickNoticer,boolean)} method where
     * the <tt>synchronous</tt> argument is set to <tt>false</tt>.
     *
     * @param resolution  The clock tick interval.
     * @param target  Object to be sent tick notifications.
     *
     * @return a new clock object according to the given parameters.
     *
     * @see TickNoticer
     */
    public Clock every(long resolution, TickNoticer target) {
        return every(resolution, target, false);
    }

    /**
     * Return the single permitted <tt>Timer</tt> instance.
     */
    static public Timer theTimer() {
        return theTimer;
    }
}
