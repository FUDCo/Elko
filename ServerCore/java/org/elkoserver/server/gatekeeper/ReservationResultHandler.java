package org.elkoserver.server.gatekeeper;

/**
 * Interface for an {@link Authorizer} object to deliver the results of
 * servicing a client's reservation request.
 */
public interface ReservationResultHandler {
    /**
     * Deliver the results of successfully processing a reservation request.
     * The various parameters should describe how to connect to the server upon
     * which the reservation was made.
     *
     * @param actor  Actor name to connect as or null to indicate that the
     *    originally requested ID is appropriate to use.
     * @param context  Name of the context to actually enter.
     * @param name  Human readable label to present, or null if not applicable.
     * @param hostport  String, in the form host:port, identifying the host and
     *    port number to connect to.
     * @param auth Authorization code to tender to the indicated host for
     *    admission.
     */
    void handleReservation(String actor, String context, String name,
                           String hostport, String auth);

    /**
     * Deliver the results of unsuccessfully processing a reservation request.
     *
     * @param failure  Error message in the case of failure or null in case of
     *    success.
     */
    void handleFailure(String failure);
}
