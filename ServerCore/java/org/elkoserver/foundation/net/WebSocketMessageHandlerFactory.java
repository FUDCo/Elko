package org.elkoserver.foundation.net;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import org.elkoserver.util.trace.Trace;

/**
 * Message handler factory to provide message handlers that wrap a message
 * stream inside a WebSocket connection.
 */
class WebSocketMessageHandlerFactory implements MessageHandlerFactory {
    /** The message handler factory for the messages embedded in the composite
        stream. */
    private MessageHandlerFactory myInnerFactory;

    /** The URI of the WebSocket connection point. */
    private String mySocketURI;

    /** Network manager for this server */
    private NetworkManager myManager;

    /** Trace object for message logging. */
    private Trace trMsg;

    /**
     * Each HTTP message handler wraps an application-level message handler,
     * which is the entity that will actually process the messages extracted
     * from the HTTP requests, so the HTTP message handler factory needs to
     * wrap the application-level message handler factory.
     *
     * @param innerFactory  The application-level message handler factor that
     *   is to be wrapped by this.
     * @param socketURI  The URI of the WebSocket connection point.
     * @param msgTrace  Trace object for message logging
     * @param manager  Network manager for this server.
     */
    WebSocketMessageHandlerFactory(MessageHandlerFactory innerFactory,
                                   String socketURI, Trace msgTrace,
                                   NetworkManager manager)
    {
        myInnerFactory = innerFactory;
        mySocketURI = socketURI;
        trMsg = msgTrace;
        myManager = manager;
    }

    private String makeErrorReply(String problem) {
        return
            "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" +
            "<html><head>\n" +
            "<title>400 Bad Request</title>\n" +
            "</head><body>\n" +
            "<h1>Bad Request</h1>\n" +
            "<p>WebSocket connection setup failed: " + problem + ".</p>\n" +
            "</body></html>\n\n";
    }

    /**
     * Transmit an HTTP error reply for a bad WS connection request.
     *
     * @param connection  The connection upon which the bad request was
     *     received.
     * @param problem  The error that is being reported
     */
    private void sendError(Connection connection, String problem) {
        if (trMsg.usage && Trace.ON) {
            trMsg.usagem(connection +
                " received invalid WebSocket connection startup: " + problem);
        }
        connection.sendMsg(new HTTPError(400, "Bad Request",
                                         makeErrorReply(problem)));
    }

    void doConnectionHandshake(Connection connection, WebSocketRequest request)
    {
        String key = request.header("sec-websocket-key");
        String key1 = request.header("sec-websocket-key1");
        String key2 = request.header("sec-websocket-key2");

        if (!request.method().equalsIgnoreCase("GET")) {
            sendError(connection, "WebSocket connection start requires GET");
        } else if (!request.URI().equalsIgnoreCase(mySocketURI)) {
            sendError(connection, "Invalid WebSocket endpoint URI");
        } else if (!"WebSocket".equalsIgnoreCase(request.header("upgrade"))) {
            sendError(connection, "Invalid WebSocket Upgrade header");
        } else if (!"Upgrade".equalsIgnoreCase(request.header("connection"))) {
            sendError(connection, "Invalid WebSocket Connection header");
        } else if (key != null) {
            connection.sendMsg(generateRidiculousHandshake6(key));
        } else if (request.crazyKey() == null) {
            sendError(connection, "Invalid WebSocket client token");
        } else if (key1 == null || key2 == null) {
            sendError(connection, "Invalid WebSocket key header");
        } else {
            connection.sendMsg(generateRidiculousHandshake0(key1, key2,
                                                          request.crazyKey()));
        }
    }

    private long insaneKeyDecode(String key) {
        int spaceCount = 0;
        long num = 0;
        int len = key.length();
        for (int i = 0; i < len; ++i) {
            char c = key.charAt(i);
            if ('0' <= c && c <= '9') {
                num = num * 10 + c - '0';
            } else if (c == ' ') {
                ++spaceCount;
            }
        }
        return num / spaceCount;
    }
    
    private WebSocketHandshake generateRidiculousHandshake0(String key1,
                                                            String key2,
                                                            byte[] crazyKey)
    {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte numBytes[] = new byte[4];

            long keyNum = insaneKeyDecode(key1);
            numBytes[0] = (byte) (keyNum >> 24);
            numBytes[1] = (byte) (keyNum >> 16);
            numBytes[2] = (byte) (keyNum >> 8);
            numBytes[3] = (byte)  keyNum;
            md5.update(numBytes);

            keyNum = insaneKeyDecode(key2);
            numBytes[0] = (byte) (keyNum >> 24);
            numBytes[1] = (byte) (keyNum >> 16);
            numBytes[2] = (byte) (keyNum >> 8);
            numBytes[3] = (byte)  keyNum;
            md5.update(numBytes);

            trMsg.debugm("Crazy key = " + String.format("%02x %02x %02x %02x %02x %02x %02x %02x", crazyKey[0], crazyKey[1], crazyKey[2], crazyKey[3], crazyKey[4], crazyKey[5], crazyKey[6], crazyKey[7]));

            return new WebSocketHandshake(0, md5.digest(crazyKey));
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("MD5 not available", e);
        }
    }

    private static final String MAGIC_WS_HANDSHAKE_GUID =
        "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WebSocketHandshake generateRidiculousHandshake6(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            String inString = key + MAGIC_WS_HANDSHAKE_GUID;
            sha1.update(inString.getBytes("ISO-8859-1"));

            return new WebSocketHandshake(6, sha1.digest());
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(
                "ISO-8859-1 encoding not available", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("SHA1 not available", e);
        }
    }


    /**
     * Provide a message handler for a new WebSocket connection.
     *
     * @param connection  The TCP connection object that was just created.
     */
    public MessageHandler provideMessageHandler(Connection connection) {
        return new WebSocketMessageHandler(connection,
            myInnerFactory.provideMessageHandler(connection));
    }

    private class WebSocketMessageHandler implements MessageHandler {
        Connection myConnection;
        MessageHandler myInnerHandler;

        WebSocketMessageHandler(Connection connection,
                                MessageHandler innerHandler)
        {
            myConnection = connection;
            myInnerHandler = innerHandler;
        }

        /**
         * Cope with connection death.
         *
         * @param connection  The connection that has just died.
         * @param reason  A possible indication why the connection went away.
         */
        public void connectionDied(Connection connection, Throwable reason) {
            myInnerHandler.connectionDied(connection, reason);
        }
        
        /**
         * Process an incoming message from a connection.
         *
         * @param connection  The connection upon which the message arrived.
         * @param message  The incoming message.
         */
        public void processMessage(Connection connection, Object message) {
            if (message instanceof WebSocketRequest) {
                doConnectionHandshake(connection, (WebSocketRequest) message);
            } else {
                myInnerHandler.processMessage(connection, message);
            }
        }
    }
}
