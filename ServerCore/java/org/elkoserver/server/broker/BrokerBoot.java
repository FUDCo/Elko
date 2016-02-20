package org.elkoserver.server.broker;

import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.boot.Bootable;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ServiceFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.foundation.server.metadata.ServiceDesc;
import org.elkoserver.util.trace.Trace;

/**
 * The Elko boot class for the Broker.  This Broker is a server allows a
 * cluster of Elko servers of various kinds to find out information about each
 * other's available services (and thus establish interconnectivity) without
 * having to be preconfigured.  It also shields the various servers from
 * order-of-startup issues.  Finally, it provides a place to stand for
 * monitoring and administering a Elko server farm as a whole.
 */
public class BrokerBoot implements Bootable {
    private Trace tr = Trace.trace("brok");
    private Broker myBroker;

    public void boot(String args[], BootProperties props) {
        Server server = new Server(props, "broker", tr);

        myBroker = new Broker(server, tr);

        if (server.startListeners("conf.listen",
                                  new BrokerServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }

        for (ServiceDesc service : server.services()) {
            service.setProviderID(0);
            myBroker.addService(service);
        }
    }

    /**
     * Service factory for the Broker.
     *
     * The Broker offers two kinds of service connections:
     *
     *    broker/client - for servers being brokered or requesting brokerage
     *    broker/admin  - for system administrators
     */
    private class BrokerServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowClient = false;
            boolean allowAdmin = false;

            if (allow.contains("any")) {
                allowClient = true;
                allowAdmin = true;
            } 
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("client")) {
                allowClient = true;
            }

            if (allowAdmin) {
                serviceNames.add("broker-admin");
            }
            if (allowClient) {
                serviceNames.add("broker-client");
            }
            
            return new BrokerActorFactory(myBroker, auth, allowAdmin,
                                          allowClient, tr);
        }
    }
}
