package org.elkoserver.foundation.timer;

/**
 * Object representing a scheduled timeout event.  Timeouts can only be created
 * by calling the {@link Timer#after after()} method on a {@link Timer}
 * instance.
 *
 * @see TimeoutNoticer
 */
public class Timeout extends TimerWatcher {
    
    private TimerThread myThread;
    private TimeoutNoticer myTarget;
    private boolean amSynchronous;

    /**
     * Package constructor to create a new Timeout object.
     *
     * @param thread  The timer thread responsible for this timeout.
     * @param target  The object to be notified at timeout time
     * @param synchronous  Flag controlling synchronous notification the
     *   timeout.  True=>notify synchronously; false=>post notification on the
     *   message queue.
     */
    Timeout(TimerThread thread, TimeoutNoticer target, boolean synchronous) {
        myThread = thread;
        myTarget = target;
        amSynchronous = synchronous;
    }

    /**
     * Cancels this timeout.  Note, however, that although a <tt>Timeout</tt>
     * can be cancelled, there is no guarantee that it has not already occured
     * by the time it is cancelled.
     *
     * @return <tt>true</tt> if cancellation was successful, <tt>false</tt> if
     *    it wasn't.
     */
    public boolean cancel() {
        if (myThread == null) {
            return false;
        } else {
            boolean result = myThread.cancelTimeout(myEvent);
            myThread = null;
            return result;
        }
    }

    /**
     * Called by the timer thread when the timeout time comes.
     */
    void handleTimeout() {
        myTarget.noticeTimeout();
    }
}
