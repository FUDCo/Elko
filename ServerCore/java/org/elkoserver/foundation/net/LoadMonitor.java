package org.elkoserver.foundation.net;

/**
 * Interface implemented by classes that support tracking server load.
 */
public interface LoadMonitor {
    /**
     * Take note of some processing time spent.
     *
     * @param timeIncrement  The amount of processing time that was spent, in
     *    milliseconds.
     */
    public void addTime(long timeIncrement);
}
