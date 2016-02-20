package org.elkoserver.server.gatekeeper;

/**
 * Descriptor object representing the results of a reservation request as
 * returned by a Director.
 */
public class ReservationResult {
    private String myContextID;
    private String myActor;
    private String myHostport;
    private String myAuth;
    private String myDeny;

    /**
     * Construct a successful reservation result.
     *
     * @param context  The context ID.
     * @param actor  The actor.
     * @param hostport  Where to connect.
     * @param auth  Authorization code to use.
     */
    ReservationResult(String contextID, String actor, String hostport,
                      String auth)
    {
        myContextID = contextID;
        myActor = actor;
        myHostport = hostport;
        myAuth = auth;
        myDeny = null;
    }

    /**
     * Construct a failed reservation result.
     *
     * @param context  The context ID.
     * @param actor  The actor.
     * @param deny  Why the reservation was denied.
     */
    ReservationResult(String contextID, String actor, String deny) {
        myContextID = contextID;
        myActor = actor;
        myHostport = null;
        myAuth = null;
        myDeny = deny;
    }

    /**
     * Get the actor ID for this result.
     *
     * @return the actor ID for this result.
     */
    public String actor() {
        return myActor;
    }

    /**
     * Get the authorization code for this result.
     *
     * @return the authorization code for this result.
     */
    public String auth() {
        return myAuth;
    }

    /**
     * Get the context ID for this result.
     *
     * @return the context ID string for this result.
     */
    public String contextID() {
        return myContextID;
    }

    /**
     * Get the error message string for this result.
     *
     * @return the error message string for this result.
     */
    public String deny() {
        return myDeny;
    }

    /**
     * Get the host:port string for this result.
     *
     * @return the host:port for this result.
     */
    public String hostport() {
        return myHostport;
    }
}
