package org.elkoserver.foundation.net;

import java.io.IOException;

/**
 * Exception to report that a connection was shutdown normally (i.e., without
 * error).
 */
public class ConnectionCloseException extends IOException {
    public ConnectionCloseException(String msg) {
        super(msg);
    }
}

