package org.elkoserver.server.context;

/**
 * Interface implemented by objects that wish to be notified when users arrive
 * in or depart from context.
 *
 * <p>This notification can be arranged by calling the {@link
 * Context#registerUserWatcher registerUserWatcher()} method on the {@link
 * Context} in which one has an interest in the comings and goings of users.
 */
public interface UserWatcher {
    /**
     * Do whatever you want when somebody arrives.
     *
     * <p>Whenever a user enters a context, the server will call this method on
     * all objects that have registered an interest in that context via the
     * context's {@link Context#registerUserWatcher registerUserWatcher()}
     * method.
     *
     * @param who  The user who arrived.
     */
    void noteUserArrival(User who);

    /**
     * Do whatever you want when somebody leaves.
     *
     * <p>Whenever a user exits a context, the server will call this method on
     * all objects that have registered an interest in that context via the
     * context's {@link Context#registerUserWatcher registerUserWatcher()}
     * method.
     *
     * @param who  The user who departed.
     */
    void noteUserDeparture(User who);
}
