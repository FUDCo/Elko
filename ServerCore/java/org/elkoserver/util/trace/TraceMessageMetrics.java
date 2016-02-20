package org.elkoserver.util.trace;

/** 
 * Trace message suitable for logging metrics for analytics.
 */
public class TraceMessageMetrics extends TraceMessage {

    /** Type label for the record.  This is a hierarchical path string. */
    private final String myType;

    /** Session or connection identifier to associate with the message. */
    private int myID;

    /** Value for the record. It must be a syntactically valid JSON value:
        string, number, boolean, or object. */
    private final String myValue;

    /**
     * Constructor.
     */
    TraceMessageMetrics(String subsystem, int level, String type, int id,
                        String value)
    {
        super(subsystem, level);
        myType = type;
        myID = id;
        myValue = value;
    }

    /** 
     * Convert this message into a string using an externally provided buffer.
     *
     * @param buffer  String builder into which to generate the string.
     */
    void stringify(StringBuilder buffer) {
        super.stringify(buffer);

        buffer.append(" : ");
        buffer.append(myType);
        buffer.append(' ');
        buffer.append(Integer.toString(myID));
        if (myValue != null) {
            buffer.append(' ');
            buffer.append(myValue);
        }
    }
}
