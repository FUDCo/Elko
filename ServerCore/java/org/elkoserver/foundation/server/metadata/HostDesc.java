package org.elkoserver.foundation.server.metadata;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.util.trace.Trace;

/**
 * Contact information for establishing a network connection to a host.
 */
public class HostDesc {
    /** Protocol spoken. */
    private String myProtocol;

    /** Where to direct clients to. */
    private String myHostPort;

    /** Authorization for connecting. */
    private AuthDesc myAuth;

    /** Retry interval for reconnect attempts, in seconds (-1 for default). */
    private int myRetryInterval;

    /** Connection retry interval default, in seconds. */
    private static final int DEFAULT_CONNECT_RETRY_TIMEOUT = 15;

    /** Flag not to log traffic on connections to this host. */
    private boolean amDontLog;

    /**
     * Constructor.
     *
     * @param protocol  Protocol spoken.
     * @param isSecure  Flag that is true if protocol is secure.
     * @param hostPort  Host/port/path to address for service.
     * @param auth  Authorization.
     * @param retryInterval  Connection retry interval, in seconds, or -1 to
     *    accept the default (currently 15).
     * @param dontLog  Flag not to log traffic when communicating to host.
     */
    public HostDesc(String protocol, boolean isSecure, String hostPort,
                    AuthDesc auth, int retryInterval, boolean dontLog)
    {
        if (isSecure) {
            myProtocol = "s" + protocol;
        } else {
            myProtocol = protocol;
        }
        myHostPort = hostPort;
        myAuth = auth;
        amDontLog = dontLog;
        if (retryInterval == -1) {
            myRetryInterval = DEFAULT_CONNECT_RETRY_TIMEOUT;
        } else {
            myRetryInterval = retryInterval;
        }
    }

    /**
     * Constructor, taking most defaults.
     *
     * <p>Equivalent to <tt>new HostDesc(protocol, false, hostPort, null,
     * -1, false)</tt>
     *
     * @param protocol  Protocol spoken.
     * @param hostPort  Host/port/path to address for service.
     */
    public HostDesc(String protocol, String hostPort) {
        this(protocol, false, hostPort, null, -1, false);
    }

    /**
     * Test if this host descriptor says not to log traffic.
     *
     * @return true if this host's "don't log" flag is set.
     */
    public boolean dontLog() {
        return amDontLog;
    }

    /**
     * Create a HostDesc object from specifications provided by properties:
     *
     * <p><tt>"<i>propRoot</i>.host"</tt> should contain a host:port
     *    string.<br>
     * <tt>"<i>propRoot</i>.protocol"</tt>, if given, should specify a protocol
     *    name.  If not given, the protocol defaults to "tcp".<br>
     * <tt>"<i>propRoot</i>.dontlog"</tt>, a boolean, if given and true,
     *    indicates that message traffic on a connection to this host should
     *    not be logged.<br>
     * <tt>"<i>propRoot</i>.retry"</tt>, an integer, if given, is the retry
     *    interval, in seconds.
     *
     * @param props  Properties to examine for a host description.
     * @param propRoot  Root property name.
     *
     * @return a new HostDesc object as specfied by 'props', or null if no such
     *    host was described.
     */
    public static HostDesc fromProperties(BootProperties props,
                                          String propRoot)
    {
        String host = props.getProperty(propRoot + ".host");
        if (host == null) {
            return null;
        } else {
            String protocol = props.getProperty(propRoot + ".protocol", "tcp");
            AuthDesc auth =
                AuthDesc.fromProperties(props, propRoot, Trace.comm);
            if (auth == null) {
                return null;
            }
            int retry = props.intProperty(propRoot + ".retry", -1);
            boolean dontLog = props.testProperty(propRoot + ".dontlog");
            
            return new HostDesc(protocol, false, host, auth, retry, dontLog);
        }
    }

    /**
     * Get this host's authorization information.
     *
     * @return this host's authorization information, or null if there isn't
     *    any (equivalent to open access).
     */
    public AuthDesc auth() {
        return myAuth;
    }

    /**
     * Get this host's contact address.
     *
     * @return this host's contact address.
     */
    public String hostPort() {
        return myHostPort;
    }

    /**
     * Get this host's protocol.
     *
     * @return this host's protocol.
     */
    public String protocol() {
        return myProtocol;
    }

    /**
     * Get this host's retry interval.
     *
     * @return this host's retry interval, in seconds.
     */
    public int retryInterval() {
        return myRetryInterval;
    }
}
