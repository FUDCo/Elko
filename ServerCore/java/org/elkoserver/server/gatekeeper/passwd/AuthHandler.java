package org.elkoserver.server.gatekeeper.passwd;

import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.gatekeeper.Gatekeeper;
import org.elkoserver.util.ArgRunnable;

/**
 * Singleton 'auth' message handler object for the password authorizer.
 */
class AuthHandler extends BasicProtocolHandler {
    /** The password authorizer object being administered. */
    private PasswdAuthorizer myAuthorizer;

    /** The gatekeeper this auth handler is working for. */
    private Gatekeeper myGatekeeper;

    /**
     * Constructor.
     *
     * @param authorizer  The password authorizer being administered.
     * @param gatekeeper  The gatekeeper this handler is working for.
     */
    AuthHandler(PasswdAuthorizer authorizer, Gatekeeper gatekeeper) {
        myAuthorizer = authorizer;
        myGatekeeper = gatekeeper;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'auth'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "auth";
    }

    /**
     * Handle the 'createactor' verb.
     *
     * Request that an actor entry be created.
     *
     * @param from  The administrator asking for the new entry.
     * @param id  The ID of the actor.
     * @param iid  The internal ID of the actor.
     * @param name  The name of the actor.
     * @param password  Password for the actor.
     * @param cansetpass  Flag that actor can set their own password.
     */
    @JSONMethod({ "id", "iid", "name", "password", "cansetpass" })
    public void createactor(BasicProtocolActor from, OptString optID,
            OptString optIID, OptString optName, OptString optPassword,
            OptBoolean optCanSetPass)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);

        String id = optID.value(null);
        boolean generated = false;
        if (id == null) {
            id = myAuthorizer.generateID();
            generated = true;
        }
        myAuthorizer.getActor(id,
                              new CreateActorRunnable(from, id, generated,
                                                      optIID.value(null),
                                                      optName.value(null),
                                                      optPassword.value(null),
                                                   optCanSetPass.value(true)));
    }

    private class CreateActorRunnable implements ArgRunnable {
        private BasicProtocolActor myFrom;
        private String myID;
        private boolean amGenerated;
        private String myInternalID;
        private String myName;
        private String myPassword;
        private boolean myCanSetPass;
        CreateActorRunnable(BasicProtocolActor from, String id,
                            boolean generated, String internalID, String name,
                            String password, boolean canSetPass)
        {
            myFrom = from;
            myID = id;
            amGenerated = generated;
            myInternalID = internalID;
            myName = name;
            myPassword = password;
            myCanSetPass = canSetPass;
        }

        public void run(Object obj) {
            if (obj != null) {
                if (amGenerated) {
                    myID = myAuthorizer.generateID();
                    myAuthorizer.getActor(myID, this);
                } else {
                    myFrom.send(msgCreateActor(AuthHandler.this, myID,
                                               "actor ID not available"));
                }
            } else {
                ActorDesc actor = new ActorDesc(myID, myInternalID, myName,
                                                myPassword, myCanSetPass);
                myAuthorizer.addActor(actor);
                myFrom.send(msgCreateActor(AuthHandler.this, myID, null));
            }
        }
    }

    /**
     * Handle the 'createplace' verb.
     *
     * Request that a place name entry be created.
     *
     * @param from  The administrator asking for the new entry.
     * @param name  The name of the new place.
     * @param context  The context that it maps to.
     */
    @JSONMethod({ "name", "context" })
    public void createplace(BasicProtocolActor from, String name,
                            String context)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);

        myAuthorizer.addPlace(name, context);
    }

    /**
     * Handle the 'deleteactor' verb.
     *
     * Request that an actor entry be removed.
     *
     * @param from  The administrator asking for the deletion.
     * @param id  The ID of the actor to be deleted.
     */
    @JSONMethod({ "id" })
    public void deleteactor(final BasicProtocolActor from, final String id)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.removeActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    from.send(msgDeleteActor(AuthHandler.this, id,
                                             (String) obj));
                }
            });
    }

    /**
     * Handle the 'deleteplace' verb.
     *
     * Request that a place name entry be removed.
     *
     * @param from  The administrator asking for the deletion.
     * @param name  The name of the entry to be deleted.
     */
    @JSONMethod({ "name" })
    public void deleteplace(BasicProtocolActor from, String name)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.removePlace(name);
    }

    /**
     * Handle the 'lookupactor' verb.
     *
     * Request that an actor entry be retrieved.
     *
     * @param from  The administrator asking for the lookup.
     * @param id  The ID of the actor to be looked up.
     */
    @JSONMethod({ "id" })
    public void lookupactor(final BasicProtocolActor from, final String id)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj == null) {
                        from.send(msgLookupActor(AuthHandler.this, id, null,
                                                 null, "no such actor"));
                    } else {
                        ActorDesc actor = (ActorDesc) obj;
                        from.send(msgLookupActor(AuthHandler.this, id,
                                                 actor.internalID(),
                                                 actor.name(), null));
                    }
                }
            });
    }

    /**
     * Handle the 'lookupplace' verb.
     *
     * Request that a place name entry be retrieved.
     *
     * @param from  The administrator asking for the lookup.
     * @param name  The name of the place to be looked up.
     */
    @JSONMethod({ "name" })
    public void lookupplace(final BasicProtocolActor from, final String name)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getPlace(name, new ArgRunnable() {
                public void run(Object obj) {
                    PlaceDesc place = (PlaceDesc) obj;
                    String contextID =
                        (place == null) ? null : place.contextID();
                    from.send(msgLookupPlace(AuthHandler.this, name,
                                             contextID));
                }
            });
    }

    /**
     * Handle the 'setcansetpass' verb.
     *
     * Request that an actor's permission to change their password be changed.
     *
     * @param from  The administrator asking for the change.
     * @param id  The ID of the actor.
     * @param cansetpass  The (new) permission setting.
     */
    @JSONMethod({ "id", "cansetpass" })
    public void setcansetpass(BasicProtocolActor from, String id,
                              final boolean canSetPass)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj != null) {
                        ActorDesc actor = (ActorDesc) obj;
                        if (actor.canSetPass() != canSetPass) {
                            actor.setCanSetPass(canSetPass);
                            myAuthorizer.checkpointActor(actor);
                        }
                    }
                }
            });
    }

    /**
     * Handle the 'setiid' verb.
     *
     * Request that an actor's internal ID be changed.
     *
     * @param from  The administrator asking for the change.
     * @param id  The ID of the actor.
     * @param iid  The (new) internal ID for the actor (empty string or omitted
     *    to remove).
     */
    @JSONMethod({ "id", "iid" })
    public void setiid(BasicProtocolActor from, String id,
                       final OptString optInternalID)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj != null) {
                        String internalID = optInternalID.value(null);
                        ActorDesc actor = (ActorDesc) obj;
                        if (!internalID.equals(actor.internalID())) {
                            actor.setInternalID(internalID);
                            myAuthorizer.checkpointActor(actor);
                        }
                    }
                }
            });
    }

    /**
     * Handle the 'setname' verb.
     *
     * Request that an actor's name be changed.
     *
     * @param from  The administrator asking for the name change.
     * @param id  The ID of the actor.
     * @param name  The (new) name for the actor (empty string or omitted to
     *    remove).
     */
    @JSONMethod({ "id", "name" })
    public void setname(BasicProtocolActor from, String id,
                        final OptString optName)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj != null) {
                        String name = optName.value(null);
                        ActorDesc actor = (ActorDesc) obj;
                        if (!name.equals(actor.name())) {
                            actor.setName(name);
                            myAuthorizer.checkpointActor(actor);
                        }
                    }
                }
            });
    }

    /**
     * Handle the 'setpassword' verb.
     *
     * Request that an actor's password be changed.
     *
     * @param from  The administrator asking for the password change.
     * @param id  The ID of the actor.
     * @param password  The (new) password for the actor (empty string or
     *    omitted to remove).
     */
    @JSONMethod({ "id", "password" })
    public void setpassword(BasicProtocolActor from, String id,
                            final OptString optPassword)
        throws MessageHandlerException
    {
        myGatekeeper.ensureAuthorizedAdmin(from);
        myAuthorizer.getActor(id, new ArgRunnable() {
                public void run(Object obj) {
                    if (obj != null) {
                        String password = optPassword.value(null);
                        ActorDesc actor = (ActorDesc) obj;
                        if (!actor.testPassword(password)) {
                            actor.setPassword(password);
                            myAuthorizer.checkpointActor(actor);
                        }
                    }
                }
            });
    }

    /**
     * Generate a 'createactor' message.
     */
    static JSONLiteral msgCreateActor(Referenceable target, String id,
                                      String failure)
    {
        JSONLiteral msg = new JSONLiteral(target, "createactor");
        msg.addParameter("id", id);
        msg.addParameterOpt("failure", failure);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'deleteactor' message.
     */
    static JSONLiteral msgDeleteActor(Referenceable target, String id,
                                      String failure)
    {
        JSONLiteral msg = new JSONLiteral(target, "deleteactor");
        msg.addParameter("id", id);
        msg.addParameterOpt("failure", failure);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'lookupactor' message.
     */
    static JSONLiteral msgLookupActor(Referenceable target, String id,
        String iid, String name, String failure)
    {
        JSONLiteral msg = new JSONLiteral(target, "lookupactor");
        msg.addParameter("id", id);
        msg.addParameterOpt("iid", iid);
        msg.addParameterOpt("name", name);
        msg.addParameterOpt("failure", failure);
        msg.finish();
        return msg;
    }

    /**
     * Generate a 'lookupplace' message.
     */
    static JSONLiteral msgLookupPlace(Referenceable target, String name,
                                      String context)
    {
        JSONLiteral msg = new JSONLiteral(target, "lookupplace");
        msg.addParameter("name", name);
        msg.addParameterOpt("context", context);
        msg.finish();
        return msg;
    }
}
