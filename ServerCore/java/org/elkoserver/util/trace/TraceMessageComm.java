package org.elkoserver.util.trace;

/** 
 * Trace message suitable for logging communications traffic.
 */
public class TraceMessageComm extends TraceMessage {

    /** Label for the connection. */
    private final String myConn;

    /** Direction flag: true=>received, false=>sent */
    private final boolean amInbound;

    /** The message itself. */
    private final String myMsg;

    /**
     * Constructor.
     */
    TraceMessageComm(String subsystem, int level, String conn, boolean inbound,
                     String msg)
    {
        super(subsystem, level);
        myConn = conn;
        amInbound = inbound;
        myMsg = msg;
    }

    /** 
     * Convert this message into a string using an externally provided buffer.
     *
     * @param buffer  String builder into which to generate the string.
     */
    void stringify(StringBuilder buffer) {
        super.stringify(buffer);

        buffer.append(" : ");
        buffer.append(myConn);
        if (amInbound) {
            buffer.append(" ->");
        } else {
            buffer.append(" <-");
        }
        if (myMsg.charAt(0) != ' ') {
            buffer.append(' ');
        }
        buffer.append(myMsg);
    }
}
