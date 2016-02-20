package org.elkoserver.foundation.net;

/**
 * Track the number of connections, so server can exit gracefully.
 */
public interface ConnectionCountMonitor {
    /**
     * Note a change in the number of connections.
     *
     * @param delta  An upward or downward adjustment to the connection count.
     */
    public void connectionCountChange(int delta);
}
