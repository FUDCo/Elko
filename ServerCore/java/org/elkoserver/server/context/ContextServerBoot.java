package org.elkoserver.server.context;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.boot.Bootable;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ServiceFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.HostDesc;
import org.elkoserver.util.trace.Trace;

/**
 * The Elko boot class for the Context Server, the basis of Elko applications
 * built on the context/user/item object model.
 */
public class ContextServerBoot implements Bootable {
    private Trace tr = Trace.trace("cont");
    private Contextor myContextor;

    public void boot(String args[], BootProperties props) {
        Server server = new Server(props, "context", tr);
        
        myContextor = new Contextor(server, tr);

        if (server.startListeners("conf.listen",
                                  new ContextServiceFactory()) == 0) {
            tr.fatalError("no listeners specified");
        }

        List<HostDesc> directors = scanHostList(props, "conf.register");
        myContextor.registerWithDirectors(directors, server.listeners());

        List<HostDesc> presencers = scanHostList(props, "conf.presence");
        myContextor.registerWithPresencers(presencers);
    }

    private class ContextServiceFactory implements ServiceFactory {
        /**
         * Provide a message handler factory for a new listener.
         *
         * @param label  The label for the listener; typically this is the root
         *    property name for the properties defining the listener attributes
         * @param auth  The authorization configuration for the listener.
         * @param allow  A set of permission keywords (derived from the
         *    properties configuring this listener) that specify what sorts of
         *    connections will be permitted through the listener.
         * @param serviceNames  A linked list to which this message should
         *    append the names of the services offered by the new listener.
         * @param protocol  The protocol (TCP, HTTP, etc.) that connections
         *    made to the new listener are expected to speak
         */
        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            if (allow.contains("internal")) {
                serviceNames.add("context-internal");
                return new InternalActorFactory(myContextor, auth, tr);
            } else {
                boolean reservationRequired;
                if (auth.mode().equals("open")) {
                    reservationRequired = false;
                } else if (auth.mode().equals("reservation")) {            
                    reservationRequired = true;
                } else {
                    tr.errorm("invalid authorization configuration for " +
                              label);
                    return null;
                }
                serviceNames.add("context-user");
                return new UserActorFactory(myContextor, reservationRequired,
                                            protocol, tr);
            }
        }
    }

    /**
     * Scan a collection of host descriptors from the server's configured
     * property info.
     *
     * @param props  Properties, from the command line and elsewhere.
     * @param propRoot  Prefix string for props describing the host set of
     *    interest
     *
     * @return a list of host descriptors for the configured collection of host
     *    information extracted from the properties.
     */
    List<HostDesc> scanHostList(BootProperties props, String propRoot) {
        int index = 0;
        List<HostDesc> hosts = new LinkedList<HostDesc>();
        while (true) {
            String hostPropRoot = propRoot;
            if (index > 0) {
                hostPropRoot += index;
            }
            ++index;
            HostDesc host = HostDesc.fromProperties(props, hostPropRoot);
            if (host == null) {
                return hosts;
            } else {
                hosts.add(host);
            }
        }
    }
}
