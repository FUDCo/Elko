package org.elkoserver.foundation.timer;

abstract class TimerWatcher {
    TimerQEntry myEvent;

    /**
     * Notification (from within the package) that the timeout has tripped.
     */
    abstract void handleTimeout();

    void setEvent(TimerQEntry event) {
        myEvent = event;
    }
}
