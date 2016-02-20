package org.elkoserver.foundation.server;

import java.util.LinkedList;
import java.util.List;
import org.elkoserver.foundation.net.LoadMonitor;
import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;

/**
 * Processing time accumulator to help keep track of system load.
 */
public class ServerLoadMonitor implements LoadMonitor {
    /** When load tracking began. */
    private long mySampleStartTime;

    /** Total time that has been spent in processing activity since counting
        began, in milliseconds. */
    private long myCumulativeProcessingTime;

    /** Objects to be notified of load samples. */
    private List<LoadWatcher> myLoadWatchers;

    /** Timeout for sampling load. */
    private Timeout myLoadSampleTimeout;

    /** Interval between load samples, in milliseconds. */
    private int myLoadSampleTimeoutTime;

    /** Default value for interval between load samples, in seconds. */
    private static final int DEFAULT_LOAD_SAMPLE_TIMEOUT = 30;

    /**
     * Constructor.
     *
     * Load begins being reckonned as of the time this is called.
     */
    ServerLoadMonitor(final Server server) {
        mySampleStartTime = System.currentTimeMillis();
        myCumulativeProcessingTime = 0;
        myLoadWatchers = new LinkedList<LoadWatcher>();
        myLoadSampleTimeoutTime =
            server.props().intProperty("conf.load.time",
                                       DEFAULT_LOAD_SAMPLE_TIMEOUT) * 1000;
        myLoadSampleTimeout = null;
        if (server.props().testProperty("conf.load.log")) {
            registerLoadWatcher(new LoadWatcher() {
                public void noteLoadSample(double factor) {
                    server.trace().debugi("Load " + factor);
                }
            });
        }
    }

    /**
     * Take note of some processing time spent.
     *
     * @param timeIncrement  The amount of processing time that was spent, in
     *    milliseconds.
     */
    public void addTime(long timeIncrement) {
        myCumulativeProcessingTime += timeIncrement;
    }

    /**
     * Add an object to the collection of objects that will be notified when
     * the server samples its load.
     *
     * @param watcher  An object to notify about load samples.
     */
    void registerLoadWatcher(LoadWatcher watcher) {
        myLoadWatchers.add(watcher);
        if (myLoadSampleTimeout == null) {
            /* Don't bother sampling until somebody starts watching. */
            scheduleLoadSampling();
        }
    }

    /**
     * Schedule a load sampling event.
     */
    private void scheduleLoadSampling() {
        myLoadSampleTimeout =
            Timer.theTimer().after(
                myLoadSampleTimeoutTime,
                new TimeoutNoticer() {
                    public void noticeTimeout() {
                        double factor = sampleLoad();
                        if (myLoadWatchers.size() > 0) {
                            for (LoadWatcher watcher : myLoadWatchers) {
                                watcher.noteLoadSample(factor);
                            }
                            scheduleLoadSampling();
                        } else {
                            myLoadSampleTimeout = null;
                        }
                    }
                });
    }

    /**
     * Remove an object from the collection of objects that are notified when
     * the server samples its load.
     *
     * @param watcher  The object to stop notifying about load samples.
     */
    void unregisterLoadWatcher(LoadWatcher watcher) {
        myLoadWatchers.remove(watcher);
    }

    /**
     * Compute the load, defined as the ratio between the cumulative processing
     * time spent and the elapsed clock time since the last time the load was
     * sampled.  Sampling the load resets the start time to the sample time and
     * zeroes the cumulative processing time accumulator.
     *
     * @return the current load estimate, as described above.
     */
    private double sampleLoad() {
        long clockTime = System.currentTimeMillis() - mySampleStartTime;
        double loadFactor = 0.0;
        if (clockTime > 0) {
            loadFactor = ((double) myCumulativeProcessingTime) /
                         ((double) clockTime);
        }
        mySampleStartTime = System.currentTimeMillis();
        myCumulativeProcessingTime = 0;
        return loadFactor;
    }
}
