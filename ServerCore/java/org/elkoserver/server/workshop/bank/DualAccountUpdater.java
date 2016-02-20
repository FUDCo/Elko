package org.elkoserver.server.workshop.bank;

/**
 * This interface is a two-account analog of the AccountUpdater interface.
 * It follows the same logic, the only difference being that it is passed two
 * accounts to modify instead of one.  See the AccountUpdater interface
 * description for further discussion.
 */
interface DualAccountUpdater {
    /**
     * Modify the account states to realize whatever dual-account update this
     * DualAccountUpdater instance is tasked with.
     *
     * @param account1  The first account object to be updated, or null if the
     *    account could not be read for some reason.
     * @param account2  The second account object to be updated, or null if the
     *    account could not be read for some reason.
     *
     * @return true if the states of both accounts were successfully changed
     *    and both should be written to repository, false, if they were not and
     *    should not.
     */
    boolean modify(Account account1, Account account2);

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
