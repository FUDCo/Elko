package org.elkoserver.foundation.timer;

/**
 * Interface implemented by objects that wish to be informed about {@link
 * Timeout} events.  A {@link Timeout} is created by calling the {@link
 * Timer#after Timer.after()} method and passing it an object that implements
 * this <tt>TimeoutNoticer</tt> interface.  The {@link #noticeTimeout} method
 * of the <tt>TimeoutNoticer</tt> object will be invoked after the indicated
 * interval.
 *
 * @see Timer#after Timer.after()
 * @see Timeout
 */
public interface TimeoutNoticer {
    /**
     * Notification of a timeout event.
     */
    void noticeTimeout();
}
