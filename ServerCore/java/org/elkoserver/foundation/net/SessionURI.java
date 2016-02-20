package org.elkoserver.foundation.net;

/**
 * Helper class to hold onto the various fragments of a parsed HTTP session
 * URI.
 */
class SessionURI {
    /* Path name elements for the four verbs. */
    private static final String CONNECT_REQ_URI    = "connect";
    private static final String SELECT_REQ_URI     = "select/";
    private static final String XMIT_REQ_URI       = "xmit/";
    private static final String DISCONNECT_REQ_URI = "disconnect/";

    /* Constants identifying verbs. */
    public static final int VERB_CONNECT    = 1;
    public static final int VERB_SELECT     = 2;
    public static final int VERB_XMIT_GET   = 3;
    public static final int VERB_XMIT_POST  = 4;
    public static final int VERB_DISCONNECT = 5;

    /** Verb from the URI, encoded as one of the constants VERB_xxx */
    public final int verb;

    /** Session ID from the URI. */
    public final long sessionID;

    /** Message sequence number from the URI. */
    public final int sequenceNumber;

    /** Set to true if parsing was successful, false if the URI was bad. */
    public final boolean valid;

    /**
     * Construct a SessionURI by parsing a URI string.
     *
     * @param uri  The URI string to parse.
     * @param rootURI  The root URI that should begin all URIs.
     *
     * The URI string must be one of the following forms:
     *
     *    ROOTURI/connect
     *    ROOTURI/connect/RANDOMCRUDTHATISIGNORED
     *    ROOTURI/xmit/SESSIONID/SEQUENCENUMBER
     *    ROOTURI/select/SESSIONID/SEQUENCENUMBER
     *    ROOTURI/disconnect/SESSIONID
     *
     * where ROOTURI is the string passed in the 'rootURI' parameter, and
     * SESSIONID and SEQUENCENUMBER are decimal integers.
     */
    public SessionURI(String uri, String rootURI) {
        String stringptr[] = new String[1];
        long initSessionID = 0;
        int initVerb = 0;
        int initSequenceNumber = 0;
        boolean initValid = false;

        if (uri.startsWith(rootURI)) {
            uri = uri.substring(rootURI.length());
            if (uri.endsWith("/")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (uri.startsWith(CONNECT_REQ_URI)) {
                if (uri.length() == CONNECT_REQ_URI.length()) {
                    uri = "";
                    initVerb = VERB_CONNECT;
                    initValid = true;
                } else if (uri.charAt(CONNECT_REQ_URI.length()) == '/') {
                    uri = uri.substring(CONNECT_REQ_URI.length() + 1);
                    initVerb = VERB_CONNECT;
                    initValid = true;
                }
            } else if (uri.startsWith(SELECT_REQ_URI)) {
                uri = uri.substring(SELECT_REQ_URI.length());
                initVerb = VERB_SELECT;
                initValid = true;
            } else if (uri.startsWith(XMIT_REQ_URI)) {
                uri = uri.substring(XMIT_REQ_URI.length());
                initVerb = VERB_XMIT_POST;
                initValid = true;
            } else if (uri.startsWith(DISCONNECT_REQ_URI)) {
                uri = uri.substring(DISCONNECT_REQ_URI.length());
                initVerb = VERB_DISCONNECT;
                initValid = true;
            }
        }
        if (initValid) {
            initValid = false;
            stringptr[0] = uri;
            if (initVerb == VERB_CONNECT) {
                initValid = true;
            } else {
                initSessionID = intComponent(stringptr);
                if (stringptr[0] != null) {
                    if (initVerb == VERB_DISCONNECT) {
                        initValid = true;
                    } else {
                        initSequenceNumber = (int) intComponent(stringptr);
                        if (stringptr[0] != null) {
                            initValid = true;
                        }
                    }
                }
            }
        }
        sessionID = initSessionID;
        sequenceNumber = initSequenceNumber;
        verb = initVerb;
        valid = initValid;
    }

    /**
     * Extract the next numeric component from a URI string (up to the next
     * '/' character or the end of string).
     *
     * @param stringptr  Length 1 array holding URI string.
     *
     * @return the integer value of the component at the head of stringptr[0].
     *
     * On exit, if parsing was successfull, stringptr[0] will point to the tail
     * of the URI string following the integer component that was extracted
     * (and following the trailing delimiter, if there was one).  If parsing
     * failed, stringptr[0] will be null.
     */
    private long intComponent(String stringptr[]) {
        String str = stringptr[0];
        int slash = str.indexOf('/');
        if (slash < 0) {
            stringptr[0] = "";
        } else {
            stringptr[0] = str.substring(slash + 1);
            str = str.substring(0, slash);
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            stringptr[0] = null;
            return -1;
        }
    }
}
