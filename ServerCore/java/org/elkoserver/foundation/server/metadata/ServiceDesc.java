package org.elkoserver.foundation.server.metadata;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;

/**
 * Description of a (possibly) registered service.
 */
public class ServiceDesc implements Encodable {
    /** The service name. */
    private String myService;

    /** Where on the network to reach the service. */
    private String myHostport;

    /** Protocol with which to speack to the service. */
    private String myProtocol;

    /** Optional label for the service. */
    private String myLabel;

    /** Authorization configuration to connect to the service. */
    private AuthDesc myAuth;

    /** Error message, or null if no errors. */
    private String myFailure;

    /** ID number indicating the provider associated with this service.  This
        is used to determine that multiple services all come from the same
        provider.  It is only maintained by the broker.  A value of -1
        indicates that no provider ID has been assigned. */
    private int myProviderID;

    /**
     * Direct constructor.
     *
     * @param service  The name of the service.
     * @param hostport  Where to reach the service.
     * @param protocol  Protocol to speak to the sevice with.
     * @param label  Printable name for the service.
     * @param auth  Authorization configuration for connection to the service.
     * @param failure  Optional error message.
     * @param providerID  Provider ID, or -1 if not set.
     */
    public ServiceDesc(String service, String hostport, String protocol,
                       String label, AuthDesc auth, String failure,
                       int providerID)
    {
        myService = service;
        myHostport = hostport;
        myProtocol = protocol;
        myLabel = label;
        if (auth == null) {
            myAuth = AuthDesc.theOpenAuth;
        } else {
            myAuth = auth;
        }
        myFailure = failure;
        myProviderID = providerID;
    }

    /**
     * Error constructor.
     *
     * @param service  The name of the service.
     * @param failure  Error message.
     */
    public ServiceDesc(String service, String failure) {
        this(service, null, null, null, null, failure, -1);
    }

    /**
     * JSON-driven constructor.
     *
     * @param service  The name of the service.
     * @param hostport  Where to reach the service.
     * @param protocol  Protocol to speak to the service with.
     * @param label  Optional printable name for the service.
     * @param auth  Optional authorization configuration for connection.
     * @param failure  Optional error message.
     * @param providerID  Optional provider ID.
     */
    @JSONMethod({ "service", "hostport", "protocol", "label", "?auth",
                  "failure", "provider" })
    public ServiceDesc(String service, OptString hostport, OptString protocol,
                       OptString label, AuthDesc auth, OptString failure,
                       OptInteger providerID)
    {
        this(service, hostport.value(null), protocol.value(null),
             label.value(null), auth, failure.value(null),
             providerID.value(-1));
    }

    /**
     * Generate a HostDesc object suitable for establishing a connection
     * to the service described by this service descriptor.
     *
     * @param retryInterval  Connection retry interval for connecting to this
     *    host, or -1 to take the default.
     *
     * @return a HostDesc for this service's host.
     */
    public HostDesc asHostDesc(int retryInterval) {
        return new HostDesc(myProtocol, false, myHostport, myAuth,
                            retryInterval, false);
    }

    /**
     * Set this service's a label string.
     *
     * @param label  The new label string for this service.
     */
    public void attachLabel(String label) {
        myLabel = label;
    }

    /**
     * Get this service's authorization configuration.
     *
     * @return this services's authorization configuration.
     */
    public AuthDesc auth() {
        return myAuth;
    }

    /**
     * Get this descriptor's error message.
     *
     * @return this descriptors's error message (or null if there is none).
     */
    public String failure() {
        return myFailure;
    }

    /**
     * Get this service's host:port string.
     *
     * @return this services's host:port string (or null if there is none).
     */
    public String hostport() {
        return myHostport;
    }

    /**
     * Get this service's label string.
     *
     * @return this service's label (or null if there is no label).
     */
    public String label() {
        return myLabel;
    }

    /**
     * Get this service's protocol string.
     *
     * @return this service's protocol (or null if there is none).
     */
    public String protocol() {
        return myProtocol;
    }

    /**
     * Get this service's provider ID.
     *
     * @return this service's provider ID (or -1 if it has none).
     */
    public int providerID() {
        return myProviderID;
    }

    /**
     * Get this services's service name.
     *
     * @return this services's service name (or null if it has none).
     */
    public String service() {
        return myService;
    }

    /**
     * Set this service's provider ID.  It is an error to set this value if it
     * has already been set.
     *
     * @param providerID  Nominal provider ID number for this service.
     */
    public void setProviderID(int providerID) {
        if (myProviderID == -1) {
            myProviderID = providerID;
        } else {
            throw new Error("attempt to set provider ID that is already set");
        }
    }

    /**
     * Generate a service descriptor based on this one.
     *
     * @param service   The service name of the sub-service.
     *
     * @return a new service descriptor with the same contact information
     *    but with a subsidary service name appended.
     */
    public ServiceDesc subService(String service) {
        String label = myLabel;
        if (label != null) {
            label += " (" + service + ")";
        }
        return new ServiceDesc(myService + "-" + service, myHostport,
            myProtocol, label, myAuth, myFailure, myProviderID);
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("servicedesc", control);

        result.addParameter("service", myService);
        result.addParameterOpt("hostport", myHostport);
        result.addParameterOpt("protocol", myProtocol);
        result.addParameterOpt("label", myLabel);
        result.addParameterOpt("auth", myAuth);
        result.addParameterOpt("failure", myFailure);
        if (myProviderID != -1) {
            result.addParameter("provider", myProviderID);
        }

        result.finish();
        return result;
    }

    /**
     * Generate a JSONLiteralArray of ServiceDesc objects from a sequence of
     * them.
     */
    public static JSONLiteralArray encodeArray(Iterable<ServiceDesc> services)
    {
        JSONLiteralArray array = new JSONLiteralArray();
        if (services != null) {
            for (ServiceDesc service : services) {
                array.addElement(service);
            }
        }
        array.finish();
        return array;
    }

    /**
     * Encode this descriptor as a single-element JSONLiteralArray.
     */
    public JSONLiteralArray encodeAsArray() {
        JSONLiteralArray array = new JSONLiteralArray();
        array.addElement(this);
        array.finish();
        return array;
    }
}

