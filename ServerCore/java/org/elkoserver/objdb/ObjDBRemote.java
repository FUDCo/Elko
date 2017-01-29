package org.elkoserver.objdb;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.json.MessageDispatcher;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.ConnectionRetrier;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.foundation.server.metadata.ServiceFinder;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONObject;
import org.elkoserver.objdb.store.ObjectDesc;
import org.elkoserver.objdb.store.ResultDesc;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Asynchronous access to a remote instance of the object database.  This is
 * implemented as a connection to an external repository.
 */
public class ObjDBRemote extends ObjDBBase {
    /** Connection to the repository, if there is one. */
    private ODBActor myODBActor;

    /** Repository requests that have been issued to this object database, the
        responses to which are still pending, either because the repository has
        not yet responded or because it is not yet connected and the requests
        haven't yet been transmitted.  Maps query tags to PendingRequest
        objects. */
    private Map<String, PendingRequest> myPendingRequests;

    /** Repository requests that haven't been transmitted due to an unconnected
        repository, in temporal order. */
    private List<PendingRequest> myUnsentRequests;

    /** Network manager, for setting up network communications. */
    private NetworkManager myNetworkManager;

    /** Contact information for the remote repository. */
    private HostDesc myRepHost;

    /** Message dispatcher for repository connections. */
    MessageDispatcher myDispatcher;

    /** Repository connection retry interval, in seconds, or -1 to take the
        default. */
    private int myRetryInterval;

    /** Flag to prevent reopening repository connection while shutting down. */
    private boolean amClosing;

    /** Trace object for logging message traffic. */
    private Trace myMsgTrace;

    /** Message handler factory for repository connections. */
    private MessageHandlerFactory myMessageHandlerFactory;

    /**
     * Create an object to access a remote object repository.
     *
     * <p>The repository to connect to is specified by configuration
     * properties, but may be indicated in one of two different ways:
     *
     * <p>The property <tt>"<i>propRoot</i>.service"</tt>, if given, indicates
     * a repository name to ask the Broker for.  Specifying this property as
     * the special value 'any' indicates that any repsitory that the Broker
     * knows about will be acceptable.
     *
     * <p>Alternatively, a repository host may be specified directly using the
     * <tt>"<i>propRoot</i>.host"</tt> property.
     *
     * <p>However the repository is indicated, the following properties are
     * also recognized:
     *
     * <p>The boolean property <tt>"<i>propRoot</i>.dontlog"</tt>, if true,
     * indicates that message traffic between this server and the remote
     * repository should not be logged, even if this server is otherwise
     * logging all message traffic.  If unspecified, it defaults to false.
     *
     * <p>The property <tt>"<i>propRoot</i>.retry"</tt> may specify a retry
     * interval (in seconds), at which successive attempts will be made to
     * connect to the external repository if earlier attempts have failed.  The
     * value -1 (which is the default if this property is left unspecified)
     * indicates that no retries should be attempted.
     *
     * <p>The property <tt>"<i>propRoot</i>.classdesc"</tt> may specify a
     * (comma-separated) list of references to class description objects to
     * read from the repository at startup time.
     *
     * @param serviceFinder  Access to broker, to locate repository server.
     * @param networkManager  Network manager, for making outbound connections.
     * @param localName  Name of this server.
     * @param props  Properties that the hosting server was configured with
     * @param propRoot  Prefix string for generating relevant configuration
     *    property names.
     * @param appTrace  Trace object for event logging.
     */
    public ObjDBRemote(ServiceFinder serviceFinder,
                       NetworkManager networkManager, final String localName,
                       BootProperties props, String propRoot, Trace appTrace)
    {
        super(appTrace);
        myODBActor = null;
        myNetworkManager = networkManager;
        amClosing = false;
        addClass("obji", ObjectDesc.class);
        addClass("stati", ResultDesc.class);
        myPendingRequests = new HashMap<String, PendingRequest>();
        myUnsentRequests = null;
        myMessageHandlerFactory = new MessageHandlerFactory() {
                public MessageHandler provideMessageHandler(Connection conn) {
                    return new ODBActor(conn, ObjDBRemote.this,
                                        localName, myRepHost, myDispatcher);
                }
            };

        loadClassDesc(props.getProperty(propRoot + ".classdesc"));
        String odbPropRoot = propRoot + ".repository";

        myRetryInterval = props.intProperty(odbPropRoot + ".retry", -1);

        String serviceName = props.getProperty(odbPropRoot + ".service");

        boolean dontLog = props.testProperty(odbPropRoot + ".dontlog");
        if (dontLog) {
            myMsgTrace = Trace.none;
        } else {
            myMsgTrace = Trace.comm;
        }

        myDispatcher = new MessageDispatcher(this);
        myDispatcher.addClass(ODBActor.class);
        if (serviceName != null) {
            myRepHost = null;
            if (serviceName.equals("any")) {
                serviceName = "repository-rep";
            } else {
                serviceName = "repository-rep-" + serviceName;
            }
            serviceFinder.findService(serviceName,
                                      new RepositoryFoundHandler(), false);
        } else {
            myRepHost = HostDesc.fromProperties(props, odbPropRoot);
            connectToRepository();
        }
    }

    private class RepositoryFoundHandler implements ArgRunnable {
        public void run(Object obj) {
            ServiceDesc[] desc = (ServiceDesc[]) obj;
            if (desc[0].failure() != null) {
                tr.errorm("unable to find repository: " + desc[0].failure());
            } else {
                myRepHost = desc[0].asHostDesc(myRetryInterval);
                connectToRepository();
            }
        }
    }

    /**
     * Start attempting to connect to the repository if the property settings
     * said to do so.
     */
    private void connectToRepository() {
        if (!amClosing) {
            if (myRepHost != null) {
                new ConnectionRetrier(myRepHost, "repository",
                                      myNetworkManager,
                                      myMessageHandlerFactory,
                                      myMsgTrace);
            }
        }
    }

    /**
     * Set the connection to the repository.
     *
     * @param odbActor  Actor representing the connection to the repository;
     *    this may be null, indicating that a connection has been lost.
     */
    void repositoryConnected(ODBActor odbActor) {
        myODBActor = odbActor;

        if (odbActor == null) {
            connectToRepository();
        } else {
            List<PendingRequest> unsentRequests = myUnsentRequests;
            myUnsentRequests = null;
            for (PendingRequest req : unsentRequests) {
                req.sendRequest(odbActor);
            }
        }
    }

    /**
     * Fetch an object from the repository.
     *
     * @param ref  Reference string naming the object desired.
     * @param collectionName  Name of collection to get from, or null to take
     *    the configured default.
     * @param handler  Handler to be called with the result.  The result will
     *    be the object requested, or null if the object could not be
     *    retrieved.
     */
    public void getObject(String ref, String collectionName,
                          ArgRunnable handler) {
        newRequest(PendingRequest.getReq(ref, collectionName, handler));
    }

    /**
     * Handle a reply from the repository to a 'get' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    void handleGetResult(String tag, ObjectDesc results[]) {
        PendingRequest req = myPendingRequests.remove(tag);
        if (req != null && results != null) {
            Object obj = null;
            String failure = results[0].failure();
            if (failure == null) {
                obj = decodeObject(req.ref(), results);
            } else {
                tr.errorm("repository error getting " + req.ref() + ": " +
                          failure);
                obj = null;
            }
            req.handleReply(obj);
        }
    }

    /**
     * Handle a reply from the repository to a 'put' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    void handlePutResult(String tag, ResultDesc results[]) {
        PendingRequest req = myPendingRequests.remove(tag);
        if (req != null && results != null) {
            req.handleReply(results[0].failure());
        }
    }

    /**
     * Handle a reply from the repository to an 'update' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    void handleUpdateResult(String tag, ResultDesc results[]) {
        PendingRequest req = myPendingRequests.remove(tag);
        if (req != null && results != null) {
            req.handleReply(results[0].failure());
        }
    }

    /**
     * Handle a reply from the repository to a 'query' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    void handleQueryResult(String tag, ObjectDesc results[]) {
        PendingRequest req = myPendingRequests.remove(tag);
        if (req != null && results != null) {
            Object obj = null;
            String failure = results[0].failure();
            /* XXX this is just wrong */
            if (failure == null) {
                obj = decodeObject(req.ref(), results);
            } else {
                tr.errorm("repository error getting " + req.ref() + ": " +
                          failure);
                obj = null;
            }
            req.handleReply(obj);
        }
    }

    /**
     * Handle a reply from the repository to a 'remove' request.
     *
     * @param tag  The tag associated with the reply.
     * @param results  The results returned.
     */
    void handleRemoveResult(String tag, ResultDesc results[]) {
        PendingRequest req = myPendingRequests.remove(tag);
        if (req != null && results != null) {
            req.handleReply(results[0].failure());
        }
    }

    /**
     * Record a new request in the pending requests table.  If currently
     * connected to the repository, also send the request to it.
     *
     * @param req  The new request.
     */
    private void newRequest(PendingRequest req) {
        myPendingRequests.put(req.tag(), req);
        if (myODBActor != null) {
            req.sendRequest(myODBActor);
        } else {
            if (myUnsentRequests == null) {
                myUnsentRequests = new LinkedList<PendingRequest>();
            }
            myUnsentRequests.add(req);
        }
    }

    /**
     * Store an object into the repository.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     *    the configured default (or the db doesn't use this abstraction).
     * @param requireNew  If true, require object 'ref' not already exist.
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void putObject(String ref, Encodable obj, String collectionName,
                          boolean requireNew, ArgRunnable handler) {
        newRequest(PendingRequest.putReq(ref, obj, collectionName, requireNew,
                                         handler));
    }

    /**
     * Update an object in the repository.
     *
     * @param ref  Reference string naming the object to be stored.
     * @param version  Version number of the object to be upated.
     * @param obj  The object to be stored.
     * @param collectionName  Name of collection to put into, or null to take
     *    the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void updateObject(String ref, int version, Encodable obj,
                             String collectionName, ArgRunnable handler) {
        newRequest(PendingRequest.updateReq(ref, version, obj, collectionName,
                                            handler));
    }

    /**
     * Query one or more objects from the object database.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     *    configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     *    indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     *    be an array of the object(s) requested, or null if no objects could
     *    be retrieved.
     */
    public void queryObjects(JSONObject template, String collectionName,
                             int maxResults, ArgRunnable handler) {
        newRequest(PendingRequest.queryReq(template, collectionName,
                                           maxResults, handler));
    }

    /**
     * Delete an object from the repository.  It is not considered an error to
     * attempt to remove an object that is not there; such an operation always
     * succeeds.
     *
     * @param ref  Reference string naming the object to remove.
     * @param collectionName  Name of collection to delete from, or null to
     *    take the configured default (or the db doesn't use this abstraction).
     * @param handler  Handler to be called with the result.  The result will
     *    be a status indicator: an error message string if there was an error,
     *    or null if the operation was successful.
     */
    public void removeObject(String ref, String collectionName,
                             ArgRunnable handler) {
        newRequest(PendingRequest.removeReq(ref, collectionName, handler));
    }

    /**
     * Shutdown the object database.
     */
    public void shutdown() {
        amClosing = true;
        if (myODBActor != null) {
            myODBActor.close();
        }
    }
}
