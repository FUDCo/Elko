package org.elkoserver.foundation.server.metadata;

import org.elkoserver.util.ArgRunnable;

/**
 * Interface for using the broker to find external services.
 */
public interface ServiceFinder {
    /**
     * Issue a request for service information to the broker.
     *
     * @param service  The service desired.
     * @param handler  Object to receive the asynchronous result(s).
     * @param monitor  If true, keep watching for more results after the first.
     */
    public void findService(String service, ArgRunnable handler,
                            boolean monitor);
}

