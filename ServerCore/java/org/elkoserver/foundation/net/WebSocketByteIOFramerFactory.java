package org.elkoserver.foundation.net;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;
import org.apache.commons.codec.binary.Base64;

/**
 * Byte I/O framer factory for WebSocket connections, a perverse hybrid of HTTP
 * and TCP.
 */
public class WebSocketByteIOFramerFactory implements ByteIOFramerFactory {
    private static Base64 theCodec = new Base64();

    private Trace trMsg;

    /** The host address of the WebSocket connection point. */
    private String myHostAddress;

    /** The host address, stripped of port number. */
    private String myHostName;

    /** The URI of the WebSocket connection point. */
    private String mySocketURI;

    /**
     * Constructor.
     *
     * @param msgTrace  Trace object for logging message traffic.
     */
    WebSocketByteIOFramerFactory(Trace msgTrace, String hostAddress,
                                 String socketURI)
    {
        trMsg = msgTrace;
        myHostAddress = hostAddress;
        int colonPos = hostAddress.indexOf(':');
        if (colonPos != -1) {
            myHostName = hostAddress.substring(0, colonPos);
        } else {
            myHostName = hostAddress;
        }
        mySocketURI = socketURI;
    }

    /**
     * Provide an I/O framer for a new HTTP connection.
     *
     * @param receiver  Object to deliver received messages to.
     * @param label  A printable label identifying the associated connection.
     */
    public ByteIOFramer provideFramer(MessageReceiver receiver, String label) {
        return new WebSocketFramer(receiver, label);
    }

    /**
     * I/O framer implementation for HTTP requests.
     */
    private class WebSocketFramer implements ByteIOFramer {
        /** The message receiver that input is being framed for. */
        private MessageReceiver myReceiver;
        
        /** A label for the connection, for logging. */
        private String myLabel;

        /** Input data source. */
        private ChunkyByteArrayInputStream myIn;

        /** Lower-level framer once we start actually reading messages. */
        private JSONByteIOFramer myMessageFramer;

        /** Message input currently in progress. */
        private String myMsgString;

        /** Stage of WebSocket input reading. */
        private int myWSParseStage;

        /** Stage is: parsing method line */
        private final static int WS_STAGE_START = 1;
        /** Stage is: parsing headers */
        private final static int WS_STAGE_HEADER = 2;
        /** Stage is: parsing handshake bytes */
        private final static int WS_STAGE_HANDSHAKE = 3;
        /** Stage is: parsing message stream */
        private final static int WS_STAGE_MESSAGES = 4;

        /** HTTP request object under construction, for start handshake. */
        private WebSocketRequest myRequest;

        /**
         * Constructor.
         */
        WebSocketFramer(MessageReceiver receiver, String label) {
            myReceiver = receiver;
            myLabel = label;
            myMsgString = "";
            myIn = new ChunkyByteArrayInputStream();
            myWSParseStage = WS_STAGE_START;
            myRequest = new WebSocketRequest();
        }
        
        /**
         * Process bytes of data received.
         *
         * @param data   The bytes received.
         * @param length  Number of usable bytes in 'data'.  End of input is
         *    indicated by passing a 'length' value of 0.
         */
        public void receiveBytes(byte[] data, int length) throws IOException {
            myIn.addBuffer(data, length);

            while (true) {
                switch (myWSParseStage) {
                    case WS_STAGE_START: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() != 0) {
                            myRequest.parseStartLine(line);
                            myWSParseStage = WS_STAGE_HEADER;
                        }
                        break;
                    }
                    case WS_STAGE_HEADER: {
                        String line = myIn.readASCIILine();
                        if (line == null) {
                            myIn.preserveBuffers();
                            return;
                        } else if (line.length() == 0) {
                            myWSParseStage = WS_STAGE_HANDSHAKE;
                        } else {
                            myRequest.parseHeaderLine(line);
                        }
                        break;
                    }
                    case WS_STAGE_HANDSHAKE: {
                        if (myRequest.header("sec-websocket-key1") != null) {
                            byte crazyKey[] = myIn.readBytes(8);
                            if (crazyKey == null) {
                                myIn.preserveBuffers();
                                return;
                            } else {
                                myRequest.setCrazyKey(crazyKey);
                            }
                        }
                        myReceiver.receiveMsg(myRequest);
                        myWSParseStage = WS_STAGE_MESSAGES;
                        myIn.setWebSocketFraming(true);
                        myMessageFramer =
                            new JSONByteIOFramer(trMsg, myReceiver, myLabel,
                                                 myIn);
                        return;
                    }
                    case WS_STAGE_MESSAGES: {
                        myMessageFramer.receiveBytes(null, 0);
                        return;
                    }
                }
            }
        }

        /**
         * Generate the bytes for writing a message to a connection.  In this
         * case, a message must be a string, a WebSocketHandshake object, or an
         * HTTPError object.  A string is considered to be a serialized JSON
         * message; it should be transmitted inside a WebSocket message
         * frame. A WebSocketHandshake object contains the information for a
         * connection setup handshake; it should be transmitted as the
         * appropriate HTTP header plus junk. An HTTPError object is just what
         * it seems to be; it should be transmitted as a regular HTTP error
         * response.
         *
         * @param msg  The message to be written.
         *
         * @return a byte array containing the writable form of 'msg'.
         */
        public byte[] produceBytes(Object msg) throws IOException {
            if (msg instanceof JSONLiteral) {
                msg = ((JSONLiteral) msg).sendableString();
            }
            if (msg instanceof String) {
                String msgString = (String) msg;
                if (trMsg.event && Trace.ON) {
                    trMsg.msgi(myLabel, false, msgString);
                }
                byte[] msgBytes = msgString.getBytes("UTF-8");
                byte[] frame = new byte[msgBytes.length + 2];
                frame[0] = 0x00;
                System.arraycopy(msgBytes, 0, frame, 1, msgBytes.length);
                frame[frame.length - 1] = (byte) 0xFF;
                if (trMsg.debug && Trace.ON) {
                    trMsg.debugm("WS sending msg: " + msg);
                }
                return frame;
            } else if (msg instanceof WebSocketHandshake) {
                WebSocketHandshake handshake = (WebSocketHandshake) msg;
                if (handshake.version() == 0) {
                    byte[] handshakeBytes = (byte[]) handshake.bytes();
                    String header =
                        "HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                        "Upgrade: WebSocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Origin: http://" + myHostName + "\r\n" +
                        "Sec-WebSocket-Location: ws://" + myHostAddress +
                            mySocketURI + "\r\n" +
                        "Sec-WebSocket-Protocol: *\r\n\r\n";
                    byte[] headerBytes = header.getBytes("ASCII");
                    byte[] reply =
                        new byte[headerBytes.length + handshakeBytes.length];
                    System.arraycopy(headerBytes, 0, reply, 0,
                                     headerBytes.length);
                    System.arraycopy(handshakeBytes, 0, reply,
                                    headerBytes.length, handshakeBytes.length);
                    if (trMsg.debug && Trace.ON) {
                        trMsg.debugm("WS sending handshake:\n" + header +
                            Trace.byteArrayToASCII(handshakeBytes, 0,
                                                   handshakeBytes.length));
                    }
                    return reply;
                } else if (handshake.version() == 6) {
                    String header =
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: Websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " +
                            theCodec.encodeToString(handshake.bytes()) +
                            "\r\n\r\n";
                    byte[] headerBytes = header.getBytes("ASCII");
                    if (trMsg.debug && Trace.ON) {
                        trMsg.debugm("WS sending handshake:\n" + header);
                    }
                    return headerBytes;
                } else {
                    throw new Error("unsupported WebSocket version");
                }
            } else if (msg instanceof HTTPError) {
                HTTPError error = (HTTPError) msg;
                String reply = error.messageString();
                reply = "HTTP/1.1 " + error.errorNumber() + " " +
                        error.errorString() + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + reply.length() + "\r\n\r\n" +
                    reply;
                if (trMsg.debug && Trace.ON) {
                    trMsg.debugm("WS sending error:\n" + reply);
                }
                return reply.getBytes("ASCII");
            } else {
                throw new IOException("unwritable message type: " +
                                      msg.getClass());
            }
        }
    }
}
