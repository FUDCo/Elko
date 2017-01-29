package org.elkoserver.foundation.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An IP network address and port number combination, represented in a somewhat
 * friendlier way than {@link java.net.InetSocketAddress InetSocketAddress}
 * does.
 */
public class NetAddr {
    /** The address.  A null means "all local IP addresses". */
    private InetAddress myInetAddress;

    /** The port number. */
    private int myPortNumber;

    /**
     * Construct a NetAddr from a string in the form:
     * <tt><i>hostName</i>:<i>portNumber</i></tt> or <tt><i>hostName</i></tt>.
     * If the <tt>:<i>portNumber</i></tt> is omitted, port number 0 will be
     * assumed.  The host name may be either a DNS name or a raw IPv4 address
     * in dotted decimal format.  Alternatively, it may be both these,
     * separated by a slash, in which case only the part after the slash is
     * significant.  If the significant part of the hostname is absent, then
     * the port is associated with all local IP addresses.
     *
     * @param addressStr  The network address string, as described above.
     *
     * @throws UnknownHostException if the host name can't be resolved.
     */
    public NetAddr(String addressStr) throws UnknownHostException {
        if (addressStr == null) {
            addressStr = "";
        }
        int colon = addressStr.indexOf(':');
        if (colon < 0) {
            myPortNumber = 0;
        } else {
            myPortNumber = Integer.parseInt(addressStr.substring(colon + 1)) ;
            addressStr = addressStr.substring(0, colon);
        }
        int slash = addressStr.indexOf('/');
        if (slash >= 0) {
            addressStr = addressStr.substring(slash + 1);
        }
        if (addressStr.length() >= 1) {
            myInetAddress = InetAddress.getByName(addressStr);
        } else {
            myInetAddress = null;
        }
    }

    /**
     * Construct a new NetAddr given an IP address and a port number.
     *
     * @param inetAddress  An IP address, where null =&gt; all local IP addresses.
     * @param portNumber  A port at that IP address.
     */
    public NetAddr(InetAddress inetAddress, int portNumber) {
        myInetAddress = inetAddress;
        myPortNumber = portNumber;
    }

    /**
     * Test if another object is a NetAddr denoting the same address as this.
     *
     * @param other  The other object to test for equality.
     * @return true if this and 'other' denote the same net address.
     */
    public boolean equals(Object other) {
        if (other == null || !(other instanceof NetAddr)) {
            return false;
        }
        NetAddr otherNetAddr = (NetAddr) other;
        if (myPortNumber != otherNetAddr.myPortNumber) {
            return false;
        }
        InetAddress otherInetAddress = otherNetAddr.myInetAddress;
        if (myInetAddress == otherInetAddress) {
            return true;
        }
        if (myInetAddress == null || otherInetAddress == null) {
            return false;
        }
        return myInetAddress.equals(otherInetAddress);
    }

    /**
     * Get the IP address.
     *
     * @return the IP address embodied by this object.
     */
    public InetAddress inetAddress() {
        return myInetAddress;
    }

    /**
     * Get the port number.
     *
     * @return the port number embodied by this object.
     */
    public int getPort() {
        return myPortNumber;
    }

    /**
     * Get a hash code for this address.
     *
     * @return a hash code that accounts for both the IP address and port.
     */
    public int hashCode() {
        int result = myPortNumber;
        if (myInetAddress == null) {
            return myPortNumber;
        } else {
            return myInetAddress.hashCode() ^ myPortNumber;
        }
    }

    /**
     * Produce a printable representation of this.
     *
     * @return a nicely formatted string representing this address.
     */
    public String toString() {
        if (myInetAddress == null) {
            return ":" + myPortNumber;
        } else {
            return myInetAddress.getHostAddress() + ":" + myPortNumber;
        }
    }
}
