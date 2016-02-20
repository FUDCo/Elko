package org.elkoserver.server.gatekeeper.passwd;

import java.security.SecureRandom;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.server.gatekeeper.Authorizer;
import org.elkoserver.server.gatekeeper.Gatekeeper;
import org.elkoserver.server.gatekeeper.ReservationResult;
import org.elkoserver.server.gatekeeper.ReservationResultHandler;
import org.elkoserver.server.gatekeeper.SetPasswordResultHandler;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * A simple implementation of the {@link Authorizer} interface for use with
 * the Elko Gatekeeper.
 */
public class PasswdAuthorizer implements Authorizer {
    /** Trace object for diagnostics. */
    private Trace tr;

    /** The database in which the authorization info is kept. */
    private ObjDB myODB;

    /** Flag controlling whether anonymous logins are to be permitted. */
    private boolean amAnonymousOK;

    /** Base string for generated actor IDs. */
    private String myActorIDBase;

    /** The gatekeeper proper. */
    private Gatekeeper myGatekeeper;

    /** Random number generator, for generating IDs. */
    static private SecureRandom theRandom = new SecureRandom();

    /**
     * Constructor.  Nothing to do in this case, since all the real
     * initialization work happens in {@link #initialize initialize()}.
     */
    public PasswdAuthorizer() {
    }

    /**
     * Initialize the authorization service.
     *
     * @param gatekeeper  The Gatekeeper this object is providing authorization
     *    services for.
     */
    public void initialize(Gatekeeper gatekeeper) {
        myGatekeeper = gatekeeper;
        tr = gatekeeper.trace();

        myODB = gatekeeper.openObjectDatabase("conf.gatekeeper");
        if (myODB == null) {
            tr.fatalError("no database specified");
        }

        myODB.addClass("place", PlaceDesc.class);
        myODB.addClass("actor", ActorDesc.class);

        BootProperties props = gatekeeper.properties();
        myActorIDBase = props.getProperty("conf.gatekeeper.idbase", "user");
        amAnonymousOK =
            !props.testProperty("conf.gatekeeper.anonymous", "false");

        gatekeeper.refTable().addRef(new AuthHandler(this, gatekeeper));
    }

    /**
     * Add an actor to the actor table.
     *
     * @param actor  The actor description for the actor to add.
     */
    void addActor(ActorDesc actor) {
        myODB.putObject("a-" + actor.id(), actor, null, false, null);
    }

    /**
     * Add a place to the place table.
     *
     * @param name  The name of the place.
     * @param context  The context 'name' maps to.
     */
    void addPlace(String name, String context) {
        PlaceDesc place = new PlaceDesc(name, context);
        myODB.putObject("p-" + name, place, null, false, null);
    }

    /**
     * Write a changed actor to the database.
     *
     * @param actor  The actor description for the actor to checkpoint.
     */
    void checkpointActor(ActorDesc actor) {
        myODB.putObject("a-" + actor.id(), actor, null, false, null);
    }

    /**
     * Generate a new, random actor ID.
     *
     * @return a new actor ID.
     */
    String generateID() {
        long idNumber = Math.abs(theRandom.nextLong());
        return myActorIDBase + "-" + idNumber;
    }

    /**
     * Obtain the description of an actor.
     *
     * @param actorID  The ID of the actor of interest.
     * @param handler  Handler to be called with the actor description object
     *    when retreived.
     */
    void getActor(String actorID, ArgRunnable handler) {
        myODB.getObject("a-" + actorID, null, handler);
    }

    /**
     * Obtain the context that a place name maps to.
     *
     * @param name  The name of the place of interest.
     * @param handler  Handler to be called with place description object when
     *    retreived.
     */
    void getPlace(String name, ArgRunnable handler) {
        myODB.getObject("p-" + name, null, handler);
    }

    /**
     * Remove an actor from the actor table.
     *
     * @param actorID  The ID of the actor to be removed.
     * @param handler  Handler to be called with deletion result.
     */
    void removeActor(String actorID, ArgRunnable handler) {
        myODB.removeObject("a-" + actorID, null, handler);
    }

    /**
     * Remove an entry from the place table.
     *
     * @param name  The name of the place to remove.
     */
    void removePlace(String name) {
        myODB.removeObject("p-" + name, null, null);
    }

    /**
     * Service a request to make a reservation.  This method is called each
     * time the Gatekeeper recieves a 'reserve' request from a client.
     *
     * @param protocol  The protocol the reservation seeker wants to use.
     * @param context  The context they wish to enter.
     * @param id  The user who is asking for the reservation.
     * @param name  Optional legible name for the user.
     * @param password  Password tendered for entry, if relevent.
     * @param handler  Object to receive results of reservation check, once
     *    available.
     */
    public void reserve(String protocol, String context, String id,
                        String name, String password,
                        ReservationResultHandler handler)
    {
        if (id == null && !amAnonymousOK) {
            handler.handleFailure("anonymous reservations not allowed");
        } else {
            ArgRunnable runnable =
                new ReserveRunnable(handler, protocol, context, id, name,
                                    password);
            if (id != null) {
                getActor(id, runnable);
            }
            getPlace(context, runnable);
        }
    }

    private class ReserveRunnable implements ArgRunnable {
        private ReservationResultHandler myHandler;
        private String myProtocol;
        private String myContextName;
        private String myID;
        private String myName;
        private String myPassword;
        private int myComponentCount;
        private ActorDesc myActor;
        private String myContextID;

        ReserveRunnable(ReservationResultHandler handler, String protocol,
                String contextName, String id, String name, String password) {
            myHandler = handler;
            myProtocol = protocol;
            myContextName = contextName;
            myID = id;
            myName = name;
            myPassword = password;
            myComponentCount = 0;
            myActor = null;
            myContextID = null;
        }

        public void run(Object obj) {
            /* This method normally gets entered twice, once for the context
               and once for the actor.  These can be distinguished by looking
               at the type of 'obj'.  However, failure to fetch an object is
               indicated by receiving a null and null is untyped, making it
               harder to sort out the failure cases.  A failure to fetch the
               context is not really a failure at all; it just means that the
               context is unnamed.  A failure to fetch the actor is a failure,
               regardless of the availability of context information.

               Basically, this means there are four possible cases:
                 -- actor + context: the success case for a named context.
                 -- actor + null: the success case for an unnamed context.
                 -- context + null: the failure case for a named context.
                 -- null + null: the failure case for an unnamed context.

               However, if 'id' is null, then the user is connecting
               anonymously and there will be no actor object arriving.  In that
               case this method will be entered just once and will always
               succeed with either a named or an unnamed context.
            */
            String failure = null;
            ReservationResult reservation = null;

            ++myComponentCount;
            if (obj != null) {
                if (obj instanceof ActorDesc) {
                    myActor = (ActorDesc) obj;
                } else if (obj instanceof PlaceDesc) {
                    myContextID = ((PlaceDesc) obj).contextID();
                } else if (obj instanceof ReservationResult) {
                    reservation = (ReservationResult) obj;
                    failure = reservation.deny();
                } else {
                    throw new Error("bad object class: " + obj.getClass());
                }
            }

            if (myComponentCount == (myID == null ? 1 : 2)) {
                if (myContextID == null) {
                    myContextID = myContextName;
                }
                String iid = null;
                if (myActor != null) {
                    if (myActor.testPassword(myPassword)) {
                        if (myName == null) {
                            myName = myActor.name();
                        }
                        myID = myActor.id();
                        iid = myActor.internalID();
                    } else {
                        failure = "bad password";
                    }
                } else if (myID != null) {
                    failure = "no such actor";
                }
                if (failure == null) {
                    myGatekeeper.requestReservation(myProtocol, myContextID,
                                                    iid, this);
                }
            }
            if (failure != null) {
                myHandler.handleFailure(failure);
            } else if (reservation != null) {
                myHandler.handleReservation(reservation.actor(),
                                            reservation.contextID(),
                                            myName,
                                            reservation.hostport(),
                                            reservation.auth());
            }
        }
    }

    /**
     * Service a request to change a user's password.  This method is called
     * each time the Gatekeeper recieves a 'setpassword' request from a client.
     *
     * @param id  The user who is asking for this.
     * @param oldPassword  Current password, to check for permission.
     * @param newPassword  The new password.
     * @param handler  Object to receive results, when done.
     */
    public void setPassword(String id, final String oldPassword,
                            final String newPassword,
                            final SetPasswordResultHandler handler)
    {
        getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    String failure = null;
                    
                    if (obj != null) {
                        ActorDesc actor = (ActorDesc) obj;
                        if (actor.testPassword(oldPassword)) {
                            if (actor.canSetPass()) {
                                actor.setPassword(newPassword);
                                checkpointActor(actor);
                                failure = null;
                            } else {
                                failure = "password change not allowed";
                            }
                        } else {
                            failure = "bad password";
                        }
                    } else {
                        failure = "no such actor";
                    }
                    handler.handle(failure);
                }
            });
    }

    /**
     * Shut down the authorization service.
     */
    public void shutdown() {
        myODB.shutdown();
    }
}
