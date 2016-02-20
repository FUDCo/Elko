package org.elkoserver.server.gatekeeper;

import java.io.IOException;
import org.elkoserver.foundation.actor.BasicProtocolActor;
import org.elkoserver.foundation.actor.RefTable;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ShutdownWatcher;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * The Gatekeeper itself as presented to its configured {@link Authorizer}
 * object.  This is the {@link Authorizer}'s linkage back to the systems that
 * do most of the real work in the Gatekeeper.
 */
public class Gatekeeper {
    /** Table for mapping object references in messages. */
    RefTable myRefTable;

    /** Server object. */
    private Server myServer;

    /** Trace object for diagnostics. */
    private Trace tr;

    /** Local auth service module. */
    private Authorizer myAuthorizer;

    /** Host description for the director. */
    private HostDesc myDirectorHost;

    /** Retry interval for connection to director, in seconds, or -1 to take
        the default. */
    private int myRetryInterval;

    /** Object for managing director connections. */
    private DirectorActorFactory myDirectorActorFactory;

    /** Trace object for controlling message logging. */
    private Trace myMsgTrace;

    /**
     * Constructor.
     *
     * @param server  Server object for this server.
     * @param appTrace  Trace object for diagnostics.
     */
    Gatekeeper(Server server, Trace appTrace) {
        myServer = server;
        tr = appTrace;

        myRefTable = new RefTable(null);

        myRefTable.addRef(new UserHandler(this));
        myRefTable.addRef(new AdminHandler(this));

        BootProperties props = server.props();

        myDirectorActorFactory =
            new DirectorActorFactory(server.networkManager(), this, tr);

        if (props.testProperty("conf.gatekeeper.director.dontlog")) {
            myMsgTrace = Trace.none;
        } else {
            myMsgTrace = Trace.comm;
        }

        myRetryInterval =
            props.intProperty("conf.gatekeeper.director.retry", -1);

        myDirectorHost = null;
        if (props.testProperty("conf.gatekeeper.director.auto")) {
            server.findService("director-user", new DirectorFoundRunnable(),
                               false);
        } else {
            HostDesc directorHost = HostDesc.fromProperties(props,
                "conf.gatekeeper.director");
            if (directorHost == null) {
                tr.errori("no director specified");
            } else {
                try {
                    setDirectorHost(directorHost);
                } catch (IOException e) {
                    tr.errorm("unable to open director connection", e);
                }
            }
        }

        String authorizerClassName =
            props.getProperty("conf.gatekeeper.authorizer",
                 "org.elkoserver.server.gatekeeper.passwd.PasswdAuthorizer");
        Class authorizerClass = null;
        try {
            authorizerClass = Class.forName(authorizerClassName);
        } catch (ClassNotFoundException e) {
            tr.fatalError("auth service class " + authorizerClassName +
                          " not found");
        }
        try {
            myAuthorizer = (Authorizer) authorizerClass.newInstance();
        } catch (IllegalAccessException e) {
            tr.fatalError("unable to access auth service constructor: " + e);
        } catch (InstantiationException e) {
            tr.fatalError("unable to instantiate auth service object: " + e);
        }
        myAuthorizer.initialize(this);

        server.registerShutdownWatcher(new ShutdownWatcher() {
                public void noteShutdown() {
                    myDirectorActorFactory.disconnectDirector();
                    myAuthorizer.shutdown();
                }
            });
    }

    private class DirectorFoundRunnable implements ArgRunnable {
        public void run(Object obj) {
            ServiceDesc[] desc = (ServiceDesc[]) obj;
            if (desc[0].failure() != null) {
                tr.errorm("unable to find director: " + desc[0].failure());
            } else {
                try {
                    setDirectorHost(desc[0].asHostDesc(myRetryInterval));
                } catch (IOException e) {
                    tr.errorm("unable to open director connection", e);
                }
            }
        }
    }

    /**
     * Get the auth service currently in use.
     *
     * @return the auth service object for this server.
     */
    Authorizer authorizer() {
        return myAuthorizer;
    }

    /**
     * Get the current director host.
     *
     * @return a host descriptor describing the current director connection.
     */
    HostDesc directorHost() {
        return myDirectorHost;
    }

    /**
     * Guard function to guarantee that an operation is being attempted by an
     * actor who is authorized to do admin operations.
     * 
     * @throws MessageHandlerException if this actor is not authorized to
     *    perform administrative operations.
     */
    public void ensureAuthorizedAdmin(BasicProtocolActor from)
        throws MessageHandlerException
    {
        if (from instanceof GatekeeperActor) {
            GatekeeperActor actor = (GatekeeperActor) from;
            actor.ensureAuthorizedAdmin();
        } else {
            from.doDisconnect();
            throw new MessageHandlerException("actor " + from +
                " attempted admin operation without authorization");
        }
    }

    /**
     * Open an asynchronous database.  The location of the database (directory
     * path or remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the
     *    database that is to be opened.
     *
     * @return an object for communicating with the open database, or
     *    null if the database location was not properly specified.
     */
    public ObjDB openObjectDatabase(String propRoot) {
        return myServer.openObjectDatabase(propRoot);
    }

    /**
     * Get the server's configuration properties.
     *
     * @return the configuration properties table for this server invocation.
     */
    public BootProperties properties() {
        return myServer.props();
    }

    /**
     * Get the object reference table for this gatekeeper.
     *
     * @return the object reference table.
     */
    public RefTable refTable() {
        return myRefTable;
    }

    /**
     * Reinitialize the server.
     */
    void reinit() {
        myServer.reinit();
    }

    /**
     * Issue a request for a reservation to the Director.
     *
     * @param protocol  The protocol for the requested reservation (i.e., the
     *    protocol that the client wishes to speak to the server providing the
     *     context).
     * @param context  The requested context.
     * @param actor  The requested actor.
     * @param handler  Object to handle the reservation result.  When the
     *    result becomes available, it will be passed as an instance of {@link
     *    ReservationResult}.
     */
    public void requestReservation(String protocol, String context,
                                   String actor, ArgRunnable handler)
    {
        if (myDirectorHost == null) {
            handler.run(new ReservationResult(context, actor,
                                            "no director host specified"));
        } else {
            myDirectorActorFactory.requestReservation(protocol, context, actor,
                                                      handler);
        }
    }

    /**
     * Get the Gatekeeper's name.  This can be useful in diagnostic log
     * messages and such.
     *
     * @return the server's name.
     */
    public String serverName() {
        return myServer.serverName();
    }

    /**
     * Change the director to which this gatekeeper is connected.  This
     * includes disconnecting from the current director (if one is connected)
     * and connecting to the new one.
     *
     * @param host  The new director host.
     */
    void setDirectorHost(HostDesc host) throws IOException {
        if (myDirectorHost != null) {
            myDirectorActorFactory.disconnectDirector();
        }
        myDirectorHost = host;
        myDirectorActorFactory.connectDirector(host);
    }

    /**
     * Shutdown the server.
     *
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    void shutdown(boolean kill) {
        myServer.shutdown(kill);
    }

    /**
     * Get the trace object.  This is used for writing messages to the server
     * log.
     *
     * @return the trace object for this server.
     */
    public Trace trace() {
        return tr;
    }
}
