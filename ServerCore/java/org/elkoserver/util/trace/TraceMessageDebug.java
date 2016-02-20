package org.elkoserver.util.trace;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/** 
 * Trace message suitable for debug output.
 */
public class TraceMessageDebug extends TraceMessage {
    /** The method that issued the trace message. */
    private final String myMethodName;

    /** 
     * Filename in which {@link #myMethodName} lives.  Note that it is not the
     * full pathname.  The JVM spec doesn't allow full pathnames to be stored
     * in the class file.
     */
    private final String myFileName;

    /**
     * Line number within the file named by {@link #myFileName}, from which the
     * message was issued.
     */
    private final String myLine;

    /** The message text itself. */
    private final String myText;

    /**
     * An arbitrary object may be attached to the message.  These are usually
     * printed with {@link #toString}, but {@link Throwable}s are handled
     * specially.
     */
    private final Object myObject;

    /**
     * Constructor.
     */
    TraceMessageDebug(String subsystem, int level, StackTraceElement frameData,
                      String text, Object object)
    {
        super(subsystem, level);
        myText = text;
        myObject = object;
        if (frameData != null) { 
            String className = frameData.getClassName();
            int dotPos = className.lastIndexOf('.');
            if (dotPos >= 0) {
                myMethodName = className.substring(dotPos+1) + '.' +
                    frameData.getMethodName();
            } else {
                myMethodName = frameData.getMethodName();
            }
            myFileName = frameData.getFileName();
            myLine = "" + frameData.getLineNumber();
        } else {
            myMethodName = "method?";
            myFileName = "file?";
            myLine = "line?";
        }
    }

    /** 
     * Convert this message into a string using an externally provided buffer.
     *
     * @param buffer  String builder into which to generated the string.
     */
    void stringify(StringBuilder buffer) {
        super.stringify(buffer);

        buffer.append(" (");
        buffer.append(myMethodName);
        buffer.append(':');
        buffer.append(myFileName);
        buffer.append(':');
        buffer.append(myLine);
        buffer.append(") ");

        buffer.append(myText);

        if (myObject != null) {
            if (myObject instanceof Throwable) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                PrintStream stream = new PrintStream(bytes);
                ExceptionManager.printStackTrace((Throwable) myObject,
                                                 stream);
                String stack = bytes.toString();
                buffer.append('\n');
                buffer.append(stack);
            } else {
                buffer.append(" : ");
                buffer.append(myObject);
            }
        }
    }
}
