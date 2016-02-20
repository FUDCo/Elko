package org.elkoserver.foundation.server;

import java.util.List;
import java.util.Set;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.server.metadata.AuthDesc;

/**
 * Interface to provide the application-specific portion of {@link
 * MessageHandlerFactory} creation.
 *
 * <p>An Elko server application provides an implementation of this interface
 * to the {@link Server} object when it asks it to start listeners based on
 * information in the server configuration file.  Whenever a listener is
 * started, that ServiceFactory is called to provide an appropriate {@link
 * MessageHandlerFactory} to provide {@link
 * org.elkoserver.foundation.net.MessageHandler MessageHandler}s for each new
 * connection accepted by that listener, based on the selection of services
 * that are configured to be offered over connections made to that listener.
 */
public interface ServiceFactory {
    /**
     * Provide a message handler factory for a new listener.
     *
     * @param label  The label for the listener; typically this is the root
     *    property name for the properties defining the listener attributes.
     * @param auth  The authorization configuration for the listener.
     * @param allow  A set of permission keywords (derived from the properties
     *    configuring this listener) that specify what sorts of connections
     *    will be permitted through the listener.
     * @param serviceNames  A mutable list to which this method should append
     *    (and thus return) the names of the services offered to connections
     *    made to the new listener.
     * @param protocol  The protocol (TCP, HTTP, etc.) that connections made
     *    to the new listener are expected to speak
     *
     * @return a new {@link MessageHandlerFactory} that  will provide message
     *    handlers for connections made to the new listener.
     */
    public MessageHandlerFactory provideFactory(String label, AuthDesc auth,
        Set<String> allow, List<String> serviceNames, String protocol);
}

