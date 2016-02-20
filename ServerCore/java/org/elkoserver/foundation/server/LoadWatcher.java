package org.elkoserver.foundation.server;

/**
 * Interface implemented by objects that register to receive periodic samplings
 * of system load.
 */
public interface LoadWatcher {
    /**
     * Take note of a load sample.
     *
     * @param loadFactor  Load factor that was sampled.
     *
     * @see Server#registerLoadWatcher(LoadWatcher)
     */
    void noteLoadSample(double loadFactor);
}
