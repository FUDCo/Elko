package org.elkoserver.objdb.store;

/**
 * Interface for an {@link ObjectStore} object to deliver the results of
 * servicing a 'get' request.
 */
public interface GetResultHandler {
    /**
     * Receive the results of a 'get' request.
     *
     * @param results  Results of the get.  The elements in the array may not
     *    necessarily correspond one-to-one with the elements of the
     *    'what' parameter of the {@link ObjectStore#getObjects ObjectStore.getObjects()}
     *    call, since the number of objects returned may vary depending on the
     *    number of contained objects.
     */
    void handle(ObjectDesc results[]);
}
