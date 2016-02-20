package org.elkoserver.foundation.net.zmq;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NullMessageHandler;
import org.elkoserver.server.context.BasicInternalObject;
import org.elkoserver.server.context.Contextor;

/**
 * Static object establish and hold onto a ZMQ outbound connection.
 */
public class ZMQOutbound extends BasicInternalObject {
    /** Address the outbound ZMQ connection connects to */
    private String myAddress;

    /** Outbound ZMQ connection to myAddress, or null if not yet open */
    private Connection myOutbound;

    /**
     * JSON-driven constructor.
     *
     * @param address  Address the outbound ZMQ connection should connect to.
     */
    @JSONMethod("address")
    public ZMQOutbound(String address) {
        myAddress = address;
    }

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     *    object table.
     * @param contextor  The contextor for this server.
     */
    public void activate(String ref, final Contextor contextor) {
        super.activate(ref, contextor);
        contextor.server().networkManager().connectVia(
            "org.elkoserver.foundation.net.zmq.ZeroMQConnectionManager",
            "",
            myAddress,
            new MessageHandlerFactory() {
                public MessageHandler provideMessageHandler(Connection conn) {
                    myOutbound = conn;
                    return new NullMessageHandler(contextor.appTrace());
                }
            },
            contextor.appTrace());

    }

    /**
     * Obtain the outbound connection.
     *
     * @return a Connection object suitable for sending JSON messages on the
     *    outbound ZMQ connection.
     */
    public Connection getConnection() {
        return myOutbound;
    }
}
