package org.elkoserver.json;

/**
 * An exception to report a syntax error when parsing a JSON object or message.
 */
public class SyntaxError extends Exception {
    public SyntaxError(String msg) {
        super(msg);
    }
    public SyntaxError() {
        super();
    }
}
