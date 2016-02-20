package org.elkoserver.foundation.timer;

/**
 * Interface implemented by classes that want to be informed about repeated
 * ticks from a {@link Clock}.  A {@link Clock} is created by calling the
 * {@link Timer#every Timer.every()} method and passing it an object which
 * implements this <tt>TickNoticer</tt> interface.  The {@link #noticeTick}
 * method of the <tt>TickNoticer</tt> object will be invoked periodically at
 * the indicated frequency.
 *
 * @see Timer#every Timer.every()
 * @see Clock
 */
public interface TickNoticer {
    /**
     * Called by clocks on their targets after each tick.
     *
     * @param ticks  Number of ticks since the calling clock was started.
     */
    void noticeTick(int ticks);
}
