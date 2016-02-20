package org.elkoserver.foundation.timer;

import java.util.TreeMap;
import org.elkoserver.util.trace.ExceptionManager;

/**
 * Thread to handle timeouts and clocks.
 */
class TimerThread extends Thread
{
    /** Collection of pending timer events, sorted by time */
    private TreeMap<TimerQEntry, TimerQEntry> myEvents;

    /** Flag to control execution */
    private boolean myRunning;

    private final static int FUDGE = 5; /* Get > 5 repeating timeouts */

    /**
     * Package level constructor
     */
    TimerThread() {
        super("Elko Timer");
        setPriority(MAX_PRIORITY);
        myEvents = new TreeMap<TimerQEntry, TimerQEntry>();
        myRunning = true;
    }

    /**
     * Cancel a previously scheduled timer event.
     *
     * @param target  Object whose event this is.
     */
    boolean cancelTimeout(TimerQEntry event) {
        synchronized (this) {
            return myEvents.remove(event) != null;
        }
    }

    /**
     * Insert a new event into the timer queue (in order).
     *
     * @param newEntry A TimerQEntry describing the new event.
     */
    private void insertEntry(TimerQEntry newEntry) {
        synchronized (this) {
            while (myEvents.get(newEntry) != null) {
                /* All times must be unique.  */
                // XXX TODO: This is a very ugly hack.  It will need to be
                // revisited should we find ourselves in a use case that
                // entails scheduling numerous events within a small time
                // window, both because the linear search for an unused time
                // position will get expensive, and because of the actual
                // delays introduced by artificially incrementing the trigger
                // time by a large amount.  Current experience is that two
                // events being scheduled at the same time is very rare, and
                // three is nearly unheard of, but this observation is based on
                // a sparsely explored application space.  The obvious fix
                // would be to replace each entry for a given time slot with a
                // list of entries, but the extra allocation overhead is not
                // yet justified by need.
                newEntry.myWhen++;
            }
            myEvents.put(newEntry, newEntry);
        }
    }

    /**
     * Return the current clock time, in milliseconds
     */
    static long queryTimerMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Run the timer thread until told to stop.
     */
    public void run() {
        while (myRunning) {
            runloop();
        }
        myEvents = null;
    }

    /**
     * The actual guts of the timer thread: Look for the next event on the
     * timer queue.  Wait until the indicated time.  Process the event and any
     * others that may now be relevent.  Repeat.
     */
    private void runloop() {
        long time;
        TimerQEntry notifies = null;
        TimerQEntry entry;

        synchronized (this) {
            if (myEvents.isEmpty()) {
                time = 0;
            } else {
                entry = (TimerQEntry) myEvents.firstKey();
                time = (entry.myWhen - queryTimerMillis()) | 1;
                /* Avoid 0 since will wait forever */
            }
        }
        synchronized (this) {
            try {
                if (time >= 0) {
                    wait(time);
                }
            } catch (Exception e) {
                /* No problem - something added or cancelled from queue */
            }
        }

        long now = queryTimerMillis();
        synchronized (this) {
            /* Only do next bunch of stuff if this timer is still running */
            if (myRunning) {
                /* Timer fired, check each element to see if it is time */
                while (!myEvents.isEmpty()) {
                    entry = (TimerQEntry) myEvents.firstKey();
                    if (entry.myWhen <= now) {
                        myEvents.remove(entry);
                        entry.myNext = notifies;
                        notifies = entry;
                    } else {
                        break;
                    }
                }
            }
        }

        /* Enumerate over notifies and notify them */
        while (myRunning && notifies != null) {
            entry = notifies;
            notifies = notifies.myNext;
            if (entry.myRepeat) {
                entry.myWhen = entry.myWhen + entry.myDelta;
                if ((entry.myWhen + (entry.myDelta*FUDGE)) < now) {
                    /* Round up in increments of entry.myDelta to maintain
                       timebase, but myDelta from "now" being rounded
                       up to the timebase */
                    long dist = (now-entry.myWhen) + entry.myDelta;
                    dist = (dist / entry.myDelta) * entry.myDelta;
                    entry.myWhen = entry.myWhen + dist;
                }
                insertEntry(entry);
            }
            TimerWatcher target = entry.myTarget;
            try {
                target.handleTimeout();
            } catch (Exception e) {
                ExceptionManager.reportException(e);
            }
        }
    }

    /**
     * Set a timeout event to happen.
     *
     * @param millis Distance into the future for event to happen
     * @param repeat true=>repeat the even every 'millis'; false=>timeout
     *   once only
     * @param target  Object which will handle the timeout event when it occurs
     */
    void setTimeout(boolean repeat, long millis, TimerWatcher target) {
        synchronized (this) {
            TimerQEntry entry = new TimerQEntry(repeat, millis, target);
            insertEntry(entry);
            target.setEvent(entry);
            if (myEvents.firstKey() == entry) {
                wakeup();
            }
        }
    }

    /**
     * Stop the thread.
     */
    void shutdown() {
        synchronized (this) {
            myRunning = false;
            wakeup();
        }
    }

    /**
     * Wake up the sleeping runloop.
     */
    private void wakeup() {
        synchronized (this) {
            try {
                notify();
            } catch (Throwable t) {
                ExceptionManager.reportException(t,
                    "TimerThread.wakeup() caught exception on notify");
            }
        }
    }
}
