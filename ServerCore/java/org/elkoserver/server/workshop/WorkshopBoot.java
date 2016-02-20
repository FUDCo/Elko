package org.elkoserver.server.workshop;

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
 * The boot class for the Workshop.  The Workshop is a server that provides a
 * place for arbitrary, configurable worker objects to run.
 */
public class WorkshopBoot implements Bootable {
    private Trace tr = Trace.trace("work");
    private Workshop myWorkshop;

    public void boot(String args[], BootProperties props) {
        Server server = new Server(props, "workshop", tr);

        myWorkshop = new Workshop(server, tr);

        if (server.startListeners("conf.listen",
                                  new WorkshopServiceFactory()) == 0) {
            tr.errori("no listeners specified");
        } else {
            myWorkshop.loadStartupWorkers();
        }
    }

    /**
     * Service factory for the Workshop.
     *
     * The Workshop offers two kinds of service connections:
     *
     *    workshop-service - for messages to objects offering services
     *    workshop-admin - for system administrators
     */
    private class WorkshopServiceFactory implements ServiceFactory {

        public MessageHandlerFactory provideFactory(String label,
                                                    AuthDesc auth,
                                                    Set<String> allow,
                                                    List<String> serviceNames,
                                                    String protocol)
        {
            boolean allowAdmin = false;
            boolean allowClient = false;

            if (allow.contains("any")) {
                allowAdmin = true;
                allowClient = true;
            }
            if (allow.contains("admin")) {
                allowAdmin = true;
            }
            if (allow.contains("workshop")) {
                allowClient = true;
            }

            if (allowAdmin) {
                serviceNames.add("workshop-admin");
            }
            if (allowClient) {
                serviceNames.add("workshop-service");
            }
            
            return new WorkshopActorFactory(myWorkshop, auth, allowAdmin,
                                            allowClient, tr);
        }
    }
}
