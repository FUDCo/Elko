package org.elkoserver.foundation.net;

/**
 * Holder for the request headers requested to be allowed by an HTTP OPTIONS
 * request, so that we can later unconditionally allow them.  Geeze.
 */
class HTTPOptionsReply {
    /** Contents of the request's Access-Control-Request-Headers header. */
    String myHeader;

    /**
     * Constructor.
     *
     * @param request  The HTTP OPTIONS request of interest, requesting
     *     cross-site resource access.
     */
    HTTPOptionsReply(HTTPRequest request) {
        myHeader = request.header("access-control-request-headers");
    }

    /**
     * Generate the Access-Control-Allow-Headers header of the reply to the
     * OPTIONS request, allowing the requestor whatever access they asked for.
     */
    String headersHeader() {
        if (myHeader != null) {
            return "Access-Control-Allow-Headers: " + myHeader + "\r\n";
        } else {
            return "";
        }
    }
}
