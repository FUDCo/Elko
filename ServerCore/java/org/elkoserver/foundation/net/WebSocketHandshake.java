package org.elkoserver.foundation.net;

class WebSocketHandshake {
    private int myVersion;
    private byte[] myBytes;

    WebSocketHandshake(int version, byte[] bytes) {
        myVersion = version;
        myBytes = bytes;
    }

    int version() {
        return myVersion;
    }

    byte[] bytes() {
        return myBytes;
    }
}
