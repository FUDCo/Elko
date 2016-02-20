package org.elkoserver.server.gatekeeper;

/**
 * Interface implemented by classes that provide an authorization policy
 * and mechanism to the Gatekeeper.
 *
 * <p>The Gatekeeper does not intrinsically implement any particular means of
 * authenticating and authorizing the users for whom it is handling
 * reservations.  Instead, it calls upon an object implementing this interface,
 * which it instantiates at server startup time.  The server takes the fully
 * qualified class name of the class of Authorizer to instantiate from the
 * <tt>"conf.gatekeeper.authorizer"</tt> configuration property.
 *
 * <p>In addition to implementing this interface, an Authorizer class must
 * also provide a zero-argument constructor).
 */
public interface Authorizer {
    /**
     * Do whatever initialization is required to begin issuing reservations.
     * This method will be called once by the Gatekeeper as part of its startup
     * procedure.
     *
     * @param gatekeeper  The Gatekeeper this Authorizer is providing
     *    authorization services for.
     */
    void initialize(Gatekeeper gatekeeper);

    /**
     * Service a request to make a reservation.  This method will be called
     * each time the Gatekeeper receives a 'reserve' request from a client.
     * The various parameters are extracted from that message.  The result,
     * once available, should be passed to the object specified by the
     * 'handler' parameter, by invoking the handler's {@link
     * ReservationResultHandler#handleReservation handleReservation()} method.
     *
     * @param protocol  The protocol the reservation seeker wants to use.
     * @param context  The context they wish to enter.
     * @param id  The user who is asking for the reservation.
     * @param name  Optional legible name for the user.
     * @param password  Password tendered for entry, if relevent.
     * @param handler  Object to receive results of reservation check, once
     *    available.
     */
    void reserve(String protocol, String context, String id, String name,
                 String password, ReservationResultHandler handler);

    /**
     * Service a request to change a user's password.  This method will be
     * called each time the Gatekeeper recieves a 'setpassword' request from a
     * client.  The various parameters are extracted from that message.  The
     * result, once available, should be passed to the object specified by the
     * 'handler' parameter, by invoking the handler's {@link
     * SetPasswordResultHandler#handle handle()} method.
     *
     * @param id  The user who is asking for this.
     * @param oldPassword  Current password, to check for permission.
     * @param newPassword  The new password.
     * @param handler  Object to receive results, when done.
     */
    void setPassword(String id, String oldPassword, String newPassword,
                     SetPasswordResultHandler handler);

    /**
     * Do any work required prior to shutting down the server.  This method
     * will be called by the Gatekeeper as part of its orderly shutdown
     * procedure.
     */
    void shutdown();
}
