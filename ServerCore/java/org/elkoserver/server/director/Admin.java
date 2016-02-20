package org.elkoserver.server.director;

import java.util.HashSet;
import java.util.Set;

/**
 * The admin facet of a director actor.  This object represents the state
 * functionality required when a connected entity is engaging in the admin
 * protocol.
 */
class Admin {
    /** The director itself. */
    Director myDirector;

    /** The actor through whom this facet communicates. */
    DirectorActor myActor;

    /** Users currently being watched. */
    private Set<String> myWatchedUsers;

    /** Contexts currently being watched. */
    private Set<String> myWatchedContexts;

    /**
     * Constructor.
     *
     * @param director  The director being administered.
     * @param actor  The actor associated with the administrator.
     */
    Admin(Director director, DirectorActor actor) {
        myDirector = director;
        myActor = actor;
        myWatchedUsers = new HashSet<String>();
        myWatchedContexts = new HashSet<String>();
    }

    /**
     * Clean up when the admin actor disconnects.
     */
    void doDisconnect() {
        for (String user : myWatchedUsers) {
            myDirector.unwatchUser(user, myActor);
        }
        for (String context : myWatchedContexts) {
            myDirector.unwatchContext(context, myActor);
        }
    }

    /**
     * Stop watching for the openings and closings of a context.
     *
     * @param contextName  The name of the context not to be watched.
     */
    void unwatchContext(String contextName) {
        myWatchedContexts.remove(contextName);
        myDirector.unwatchContext(contextName, myActor);
    }

    /**
     * Stop watching for the arrivals and departures of a user.
     *
     * @param userName  The name of the user not to be watched.
     */
    void unwatchUser(String userName) {
        myWatchedUsers.remove(userName);
        myDirector.unwatchUser(userName, myActor);
    }

    /**
     * Watch for the openings and closings of a context.
     *
     * @param contextName  The name of the context to be watched.
     */
    void watchContext(String contextName) {
        myWatchedContexts.add(contextName);
        myDirector.watchContext(contextName, myActor);
    }

    /**
     * Watch for the arrivals and departures of a user.
     *
     * @param userName  The name of the user to be watched.
     */
    void watchUser(String userName) {
        myWatchedUsers.add(userName);
        myDirector.watchUser(userName, myActor);
    }
}
