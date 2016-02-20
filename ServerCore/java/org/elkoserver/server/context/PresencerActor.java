package org.elkoserver.server.context;

import org.elkoserver.foundation.actor.NonRoutingActor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Actor representing a connection to a director.
 */
class PresencerActor extends NonRoutingActor {
    /** Send group containing all the director connections. */
    private PresencerGroup myGroup;

    /**
     * Constructor.
     *
     * @param connection  The connection for actually communicating.
     * @param dispatcher  Message dispatcher for incoming messages.
     * @param group  The send group for all the directors.
     * @param host  Host description for this connection.
     */
    PresencerActor(Connection connection, MessageDispatcher dispatcher,
                   PresencerGroup group, HostDesc host) {
        super(connection, dispatcher);
        myGroup = group;
        group.admitMember(this);
        send(msgAuth(this, host.auth(), group.contextor().serverName()));
    }

    /**
     * Handle loss of connection from the director.
     *
     * @param connection  The director connection that died.
     * @param reason  Exception explaining why.
     */
    public void connectionDied(Connection connection, Throwable reason) {
        Trace.comm.eventm("lost director connection " + connection + ": " +
                          reason);
        myGroup.expelMember(this);
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'provider'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "presence";
    }

    /**
     * Handle the 'gtou' verb.
     *
     * Process a notification about a group of users to a particular user
     *
     * @param userRef  The user who may be interested in this
     * @param context  The context the user is in
     * @param group  Info about the other users whose presences are online
     */
    @JSONMethod({ "touser", "ctx", "group" })
    public void gtou(PresencerActor from, String userRef, String contextRef,
                     GToUDomainInfo group[]) throws MessageHandlerException
    {
        for (GToUDomainInfo info : group) {
            for (GToUFriendInfo friend : info.friends) {
                myGroup.contextor().observePresenceChange(
                    contextRef, userRef, info.domain, friend.user,
                    friend.userMeta, friend.context, friend.contextMeta, true);
            }
        }
    }

    static private class GToUDomainInfo {
        final String domain;
        final GToUFriendInfo friends[];
        @JSONMethod({ "domain", "friends" })
            GToUDomainInfo(String domain, GToUFriendInfo friends[]) {
            this.domain = domain;
            this.friends = friends;
        }
    }

    static private class GToUFriendInfo {
        final String user;
        final JSONObject userMeta;
        final String context;
        final JSONObject contextMeta;
        @JSONMethod({ "user", "?umeta", "ctx", "?cmeta" })
        GToUFriendInfo(String user, JSONObject userMeta, String context,
                       JSONObject contextMeta)
        {
            this.user = user;
            this.userMeta = userMeta;
            this.context = context;
            this.contextMeta = contextMeta;
        }
    }

    /**
     * Handle the 'utog' verb.
     *
     * Process a notification about a user to a group of users
     *
     * @param userRef  The user about whom this notification concerns
     * @param userMeta  Optional user metadata
     * @param contextRef  The context that this user has entered or exited
     * @param contextMeta  Optional context metadata
     * @param on  Flag indicating whether the presence is coming online or not
     * @param conns  List of users who may be interested in this
     */
    @JSONMethod({ "user", "?umeta", "ctx", "?cmeta", "on", "togroup" })
    public void utog(PresencerActor from, String userRef, JSONObject userMeta,
                     String contextRef, JSONObject contextMeta, boolean on,
                     UToGDomainInfo toGroup[])
        throws MessageHandlerException
    {
        for (UToGDomainInfo domainInfo : toGroup) {
            for (UToGContextInfo contextInfo : domainInfo.who) {
                for (String friend : contextInfo.users) {
                    myGroup.contextor().observePresenceChange(
                        contextInfo.context, friend, domainInfo.domain,
                        userRef, userMeta, contextRef, contextMeta, on);
                }
            }
        }
    }

    static private class UToGDomainInfo {
        final String domain;
        final UToGContextInfo who[];
        @JSONMethod({ "domain", "who" })
        UToGDomainInfo(String domain, UToGContextInfo who[]) {
            this.domain = domain;
            this.who = who;
        }
    }

    static private class UToGContextInfo {
        final String context;
        final String users[];
        @JSONMethod({ "ctx", "users" })
        UToGContextInfo(String context, String users[]) {
            this.context = context;
            this.users = users;
        }
    }
}
