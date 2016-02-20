package org.elkoserver.foundation.net;

/**
 * An HTTP request descriptor, augmented for setting up a WebSocket connection.
 */
public class WebSocketRequest extends HTTPRequest {
    /** Goofy byte string at the start of the request body, used as part of the
        insane connection initiation protocol specified by some versions of the
        WebSockets spec. */
    private byte[] myCrazyKey;

    /**
     * Get the crazy handshake key.
     *
     * @return the 8 bytes from the start of the request body.
     */
    public byte[] crazyKey() {
        return myCrazyKey;
    }

    void setCrazyKey(byte[] crazyKey) {
        myCrazyKey = crazyKey;
    }
}

