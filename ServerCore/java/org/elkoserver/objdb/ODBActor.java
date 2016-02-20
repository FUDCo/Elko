package org.elkoserver.objdb;

import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.objdb.store.ResultDesc;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a connection to a repository.
 */
class ODBActor extends NonRoutingActor
{
    /** Local interface to remote repository this actor feeds into. */
    private ObjDBRemote myODB;

    /**
     * Constructor.
     *
     * @param connection  The connection for actually communicating to the
     *    repository.
     * @param odb  Local interface to the remote repository.
     * @param localName  Name of this server.
     * @param host  Description of repository host address.
     * @param dispatcher  Message dispatcher for repository actors.
     */
    ODBActor(Connection connection, ObjDBRemote odb, String localName,
             HostDesc host, MessageDispatcher dispatcher)
    {
        super(connection, dispatcher);
        myODB = odb;
        send(msgAuth(this, host.auth(), localName));
        odb.repositoryConnected(this);
    }

    /**
     * Handle loss of connection from the repository.
     *
     * @param connection  The repository connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        Trace.comm.eventm("lost repository connection " + connection + ": " +
                          reason);
        myODB.repositoryConnected(null);
    }

    /**
     * Get this object's reference string.  This singleton object's reference
     * string is always 'rep'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "rep";
    }

    /**
     * Handle the 'get' verb.
     *
     * Process the reply to an earlier 'get' request.
     */
    @JSONMethod({ "tag", "results" })
    public void get(ODBActor from, OptString tag, ObjectDesc results[]) {
        myODB.handleGetResult(tag.value(null), results);
    }

    /**
     * Handle the 'put' verb.
     *
     * Process the reply to an earlier 'put' request.
     */
    @JSONMethod({ "tag", "results" })
    public void put(ODBActor from, OptString tag, ResultDesc results[]) {
        myODB.handlePutResult(tag.value(null), results);
    }

    /**
     * Handle the 'update' verb.
     *
     * Process the reply to an earlier 'update' request.
     */
    @JSONMethod({ "tag", "results" })
    public void update(ODBActor from, OptString tag, ResultDesc results[]) {
        myODB.handleUpdateResult(tag.value(null), results);
    }

    /**
     * Handle the 'query' verb.
     *
     * Process the reply to an earlier 'query' request.
     */
    @JSONMethod({ "tag", "results" })
    public void query(ODBActor from, OptString tag, ObjectDesc results[]) {
        myODB.handleQueryResult(tag.value(null), results);
    }

    /**
     * Handle the 'remove' verb.
     *
     * Process the reply to an earlier 'remove' request.
     */
    @JSONMethod({ "tag", "results" })
    public void remove(ODBActor from, OptString tag, ResultDesc results[]) {
        myODB.handleRemoveResult(tag.value(null), results);
    }
}
