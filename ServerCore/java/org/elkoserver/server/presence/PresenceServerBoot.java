package org.elkoserver.server.presence;

import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.boot.Bootable;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.Server;
import org.elkoserver.foundation.server.ServiceFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;
import org.elkoserver.util.trace.Trace;

/**
 * The Elko boot class for the Presence Server.  This server allows a group of
 * Context Servers to keep track of the online presences of the various other
 * users in their own users' social graphs.
 */
public class PresenceServerBoot implements Bootable {
    private Trace tr = Trace.trace("pres");
    private PresenceServer myPresenceServer;

    public void boot(String args[], BootProperties props) {
        Server server = new Server(props, "presence", tr);

        myPresenceServer = new PresenceServer(server, tr);

        if (server.startListeners("conf.listen",
                                  new PresenceServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        }
    }

    /**
     * Service factory for the Presence Server.
     *
     * The Presence Server offers two kinds of service connections:
     *
     *    presence/client - for context servers monitoring presence information
     *    presence/admin  - for system administrators
     */
    private class PresenceServiceFactory implements ServiceFactory {

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
                serviceNames.add("presence-admin");
            }
            if (allowClient) {
                serviceNames.add("presence-client");
            }
            
            return new PresenceActorFactory(myPresenceServer, auth, allowAdmin,
                                            allowClient, tr);
        }
    }
}
