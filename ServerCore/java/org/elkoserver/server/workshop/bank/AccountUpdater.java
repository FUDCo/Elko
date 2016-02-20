package org.elkoserver.server.workshop.bank;

/**
 * This interface is implemented by objects that perform atomic updates to the
 * persistent state of an account.  A new instance of AccountUpdater should be
 * created for each act of account updating, since multiple such updates may be
 * going on concurrently.  The AccountUpdater object should be created at the
 * start of the operation.  It can maintain any intermediate short-term state
 * required during the execution of the operation, and then will be discarded
 * when the operation is finished.
 *
 * Account update is executed in two stages.  The first stage is implemented by
 * the modify() method, the second by the complete() method.
 *
 * The modify() method has the job of performing whatever actual manipulation
 * to the account state is required during the "modify" portion of the atomic
 * read/modify/write cycle that constitutes an account update operation (the
 * read and write portions of the cycle will be performed by the bank object
 * that invokes modify()).  The modify() method is passed an account object
 * representing what was read from the repository.  If that read failed (for
 * example, if the requested account ref did not designate a valid account),
 * this will be indicated by passing a null value for the account.  The
 * modify() method is expected to do its work by altering the state of the
 * account object it was given, and then return a boolean indicating its
 * success or failure at so doing: true for success, false for failure.  The
 * modify() method may succeed or fail based on whatever criteria it chooses,
 * but typically failure will be due to the account's unsuitability for the
 * purpose intended (for example, insufficient funds in the case of an
 * attempted withdrawal).  Note, however, that modify() MUST fail if the
 * Account object given is null.
 *
 * If modify() returns a success result, the bank will attempt to write the
 * changed state of the account back to the repository.  This write may itself
 * fail due to system issues or because the stored state of the account was
 * changed by somebody else between the time it was originally read and the
 * write attempt.  If the write failed due to an intervening state change, the
 * bank will re-read the account and call modify() again with the new account
 * state.  This retry process will repeat until an updated account state is
 * successfully written or until the write has failed due to an unrecoverable
 * error.  Because this retry logic can result in modify() being called again
 * after it has succeeded, in the success case the method must not cause any
 * side effects to any objects other than AccountUpdater itself or the Account
 * object given; in particular, in such as case there should be no
 * communication with the client.  However, side effects, including
 * communication, are permissible when modify() fails, since in such a case
 * modify() will not be called again.
 *
 * After a write of updated account state has completed successfully, or after
 * it has been determined that such a write will never succeed, the modify
 * stage is done.  At this point, complete() will be invoked.  The complete()
 * method has the job of performing whatever actions may be required after the
 * modify stage has finished.  Typically this will consist of notifying the
 * client about the results of the operation.
 *
 * The complete() method has one parameter, an indicator of the success or
 * failure of the final write operation. If any other information is needed by
 * complete(), the class implementing this interface must store that
 * information within the AccountUpdater instance itself as part of the action
 * of the modify() method.  Note that even though successive calls to modify()
 * may be passed different Account objects, the AccountUpdater may always
 * retain a reference to the most recent Account object, and the bank
 * guarantees that this Account instance will not be modified externally
 * between the time modify() returns and the time complete() is called.
 */
interface AccountUpdater {
    /**
     * Modify the account state to realize whatever account update operation
     * this AccountUpdater instance is tasked with.
     *
     * @param account  The account object to be updated, or null if the account
     *    could not be read for some reason.
     *
     * @return true if the state of 'account' was successfully changed and
     *    should be written to the repository, false, if it was not and should
     *    not.
     */
    boolean modify(Account account);

    /**
     * Take any actions desired after the final invocation of the modify()
     * method, such as transmitting a result notification to the client.
     *
     * @param failure   A string describing the failure of the write operation
     *    at the end of the modification stage.  This will be null if the
     *    write succeeded.
     */
    void complete(String failure);
}
