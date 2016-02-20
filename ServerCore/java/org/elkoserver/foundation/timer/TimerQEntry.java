package org.elkoserver.foundation.timer;

/**
 * An entry in the timer event queue.
 */
class TimerQEntry implements Comparable
{
    boolean myRepeat;
    long myDelta;
    long myWhen;
    TimerWatcher myTarget;
    TimerQEntry myNext;

    TimerQEntry(boolean repeat, long delta, TimerWatcher target) {
        myRepeat = repeat;
        myDelta = delta;
        myWhen = TimerThread.queryTimerMillis() + delta;
        myTarget = target;
        myNext = null;
    }

    public int compareTo(Object obj) {
        if (obj instanceof TimerQEntry) {
            TimerQEntry other = (TimerQEntry) obj;
            if (myWhen < other.myWhen) {
                return -1;
            } else if (myWhen == other.myWhen) {
                return 0;
            } else {
                return 1;
            }
        } else {
            throw new ClassCastException();
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof TimerQEntry) {
            TimerQEntry other = (TimerQEntry) obj;
            return myWhen == other.myWhen;
        } else {
            return false;
        }
    }
}
