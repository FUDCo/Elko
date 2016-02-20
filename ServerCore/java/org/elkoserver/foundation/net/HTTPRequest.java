package org.elkoserver.foundation.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import org.elkoserver.util.trace.Trace;

/**
 * An HTTP request descriptor, obtained by parsing the lines of text in an HTTP
 * request as they are received.
 */
public class HTTPRequest {
    /** The method from the HTTP start line. */
    private String myMethod = null;

    /** The URI from the HTTP start line. */
    private String myURI = null;

    /** All the headers, indexed by name. */
    private Map<String, String> myHeaders;

    /** Value of the Content-Length header. */
    private int myContentLength = 0;

    /** Flag whether Connection header says "close". */
    private boolean amNonPersistent = false;

    /** Flag whether Content-Type header says URL encoding is in use. */
    private boolean amURLEncoded = false;

    /** The message body, if there is one. */
    private String myContent = null;

    /**
     * Create a new HTTP request descriptor.  The descriptor still needs to be
     * filled in.
     */
    HTTPRequest() {
        myHeaders = new HashMap<String, String>();
    }

    /**
     * Get the message body content.
     *
     * @return the request message body content as a string, or null if there
     *    is none.
     */
    public String content() {
        return myContent;
    }

    /**
     * Get the message body length.
     *
     * @return the length of the message body, or 0 if there is no body.
     */
    int contentLength() {
        return myContentLength;
    }

    /**
     * Get the value of a request header field.
     *
     * @param name  The header name whose value is desired.
     *
     * @return the value of the header named by 'name', or null if there is no
     *    such header in the request.
     */
    public String header(String name) {
        return myHeaders.get(name);
    }

    /**
     * Test if this is a non-persistent connection.
     *
     * @return true if a header line said "Connection: close".
     */
    public boolean isNonPersistent() {
        return amNonPersistent;
    }

    /**
     * Get the request method (GET, PUT, etc.).
     *
     * @return the request method.
     */
    public String method() {
        return myMethod;
    }

    /**
     * Parse a header line, adding the header it contains to the header table.
     *
     * @param line  The line to be parsed.
     */
    void parseHeaderLine(String line) {
        line = line.trim();
        int colon = line.indexOf(':');
        if (0 < colon && colon < line.length() - 1) {
            String name = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon+1).trim();
            myHeaders.put(name, value);
            if (name.equals("content-length")) {
                myContentLength = Integer.parseInt(value);
            } else if (name.equals("connection")) {
                amNonPersistent = value.equalsIgnoreCase("close");
            } else if (name.equals("content-type")) {
                amURLEncoded =
                   value.equalsIgnoreCase("application/x-www-form-urlencoded");
            }
        }
    }

    /**
     * Parse an HTTP start line, extracting the method name and URI.
     *
     * @param line  The line to be parsed.
     */
    void parseStartLine(String line) {
        line = line.trim();
        int methodEnd = line.indexOf(' ');
        if (methodEnd >= 0) {
            myMethod = line.substring(0, methodEnd);
            ++methodEnd;
            int uriEnd = line.indexOf(' ', methodEnd);
            if (uriEnd >= 0) {
                myURI = line.substring(methodEnd, uriEnd).toLowerCase();
            }
        }
    }

    /**
     * Record the request's message body content.
     *
     * @param body  The body itself.
     */
    void setContent(String content) {
        if (amURLEncoded) {
            try {
                myContent = URLDecoder.decode(content, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                /* This should never happen. */
                Trace.comm.errorm("bogus " + e);
                myContent = content;
            }
        } else {
            myContent = content;
        }
    }

    /**
     * Obtain a printable String representation of this request.
     *
     * @return a printable dump of the request state.
     */
    public String toString() {
        String result = "HTTP Request " + myMethod + " for " + myURI + "\n";

        for (Map.Entry<String, String> entry : myHeaders.entrySet()) {
            result += entry.getKey() + ": " + entry.getValue() + "\n";
        }

        if (myContent == null) {
            result += "Content: <none>\n";
        } else {
            result += "Content: /" + myContent + "/\n";
        }
        return result;
    }

    /**
     * Get the URI that was requested.
     *
     * @return the requested URI.
     */
    public String URI() {
        return myURI;
    }
}

