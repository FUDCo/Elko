package org.elkoserver.util.trace;

/** 
 * Trace message suitable for output of simple notices (e.g., version ID,
 * copyright declaration, etc).
 */
public class TraceMessageInfo extends TraceMessage {

    /** The message text itself. */
    private final String myText;

    /**
     * Constructor.
     */
    TraceMessageInfo(String subsystem, int level, String text)
    {
        super(subsystem, level);
        myText = text;
    }

    /** 
     * Convert this message into a string using an externally provided buffer.
     *
     * @param buffer  String builder into which to generate the string.
     */
    void stringify(StringBuilder buffer) {
        super.stringify(buffer);

        buffer.append(" : ");
        buffer.append(myText);
    }
}
