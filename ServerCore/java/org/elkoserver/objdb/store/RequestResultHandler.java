package org.elkoserver.objdb.store;

/**
 * Interface for an {@link ObjectStore} object to deliver the results of
 * servicing a simple request, such as 'put' or 'remove', that does not return
 * any objects but only a status indication.
 */
public interface RequestResultHandler {
    /**
     * Receive the results of a request.
     *
     * @param results  Results of the operation.  The elements in the array
     *    will correspond one-to-one with the elements of the 'what' parameter
     *    of the request initiating the action reported on here.
     */
    void handle(ResultDesc results[]);
}
