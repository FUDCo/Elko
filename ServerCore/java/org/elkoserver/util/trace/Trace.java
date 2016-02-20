package org.elkoserver.util.trace;

import java.util.HashMap;
import java.util.Map;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;

/**
 * This class provides output to the server log on behalf of a particular
 * server subsystem.  A number of frequently used trace subsystem objects are
 * available as static variables on this class.  Users can also create
 * additional trace objects as needed for their own purposes.<p>
 *
 * A collection of trace priority thresholds, represented by a group of boolean
 * variables, control which trace messages are actually output.  These
 * variables can be tested by a user of the trace code to quickly decide
 * whether to call a trace method (thus helping avoid the costs of procedure
 * call and string manipulation in cases where no output would be generated
 * anyway).
 */
public class Trace {
    /**
     * The Trace objects for subsystems being traced, indexed by subsystem
     * name.
     */
    static private Map<String, Trace> theTraces =
        new HashMap<String, Trace>();

    /*
     * The different trace thresholds.  See the Trace class for documentation.
     * There is space between the levels for expansion.  If you add or delete
     * a level, you must change Trace.java to add new methods and variables.
     */

    /** "Notice" level (not thresholded) */
    final public static int NOTICE = 20000;

    /** "Metrics" level (not thresholded) */
    final public static int METRICS = 20001;

    /** "Error" level trace threshold */
    final public static int ERROR = 10000;  /* Always on */

    /** "Warning" level trace threshold */
    final public static int WARNING = 120;

    /** "World" level trace threshold */
    final public static int WORLD = 100;

    /** "Usage" level trace threshold */
    final public static int USAGE = 80;

    /** "Event" level trace threshold */
    final public static int EVENT = 60;

    /** "Message" level trace threshold.  Essentially the save as "Event", but
        flagged differently in log entries. */
    final public static int MESSAGE = 59;

    /** "Debug" level trace threshold */
    final public static int DEBUG = 40;

    /** "Verbose" level trace threshold */
    final public static int VERBOSE = 20;

    /** 
     * This variable statically controls whether tracing is enabled at all.  By
     * compiling against a version of this class that has this variable set to
     * <tt>false</tt> and testing this prior to invoking trace operations, one
     * can arrange to have the trace calls removed by the compiler.
     */
    static public final boolean ON = true;

    /** 
     * Flag to control tracing of error messages.  Error messages report on
     * some internal error.  They don't necessarily lead to the system
     * stopping, but they might.  Error messages are always logged.
     */
    public boolean error;

    /**
     * Flag to control tracing of warning messages.  Warning messages are not
     * as serious as errors, but they're signs of something odd.
     */
    public boolean warning;

    /**
     * Flag to control tracing of world messages.  World messages track the
     * state of the world as a whole.   They are the sort of things server
     * operators ask for specifically, such as "can you tell me when someone
     * connects."   They should appear only occasionally.
     */
    public boolean world;

    /**
     * Flag to control tracing of usage messages.  Usage messages are used to
     * answer the question "who did what up to the point the bug appeared?"
     * They are also used to collect higher-level usability information.
     */
    public boolean usage;

    /**
     * Flag to control tracing of event messages.  Event messages describe the
     * major actions the system takes in response to user actions.  The
     * distinction between this category and debug is fuzzy, especially since
     * debug is already used for many messages of this type.  However, it can
     * be used to log specific user actions for usability testing, and to log
     * information for testers.  
     */
    public boolean event;

    /**
     * Flag to control tracing of debug messages.  Debug messages provide more
     * detail for people who want to delve into what's going on, probably to
     * figure out a bug.   
     */
    public boolean debug;

    /**
     * Flag to control tracing of verbose messages.  Verbose messages provide
     * even more detail than debug.  They're probably mainly used when first
     * getting code to work.  
     */
    public boolean verbose;

    /**
     * Current tracing threshold.  Semi-redundant with the various booleans
     * listed above.  The boolean flags are provided principally to allow fast
     * and efficient checking of individual trace control levels.  However,
     * although the flags *may* be set individually, more often tracing is
     * controlled by setting the threshold, which effects various flags in a
     * coordinated fashion.
     */
    private int myThreshold;

    /** Flag that the threshold is defaulted. */
    private boolean myThresholdIsDefaulted;


    /*
     *  Predefined subsystems.  
     */

    /** Trace object for the 'comm' (communications) subsystem.  In particular,
     * message traffic is logged when the trace level of this object is set to
     * <tt>EVENT</tt> or above. */
    static public Trace comm = trace("comm");

    /** Trace object for the 'none' subsystem.  This is a trace object that
     * will never log under any circumstances.  It can be passed in method
     * parameters that require a trace object but you don't want any tracing to
     * result. */
    static public Trace none = trace("none");

    /** Trace object for the 'runq' (run queue) subsystem.  This is used for
     * log messages from the event queue manager. */
    static public Trace runq = trace("runq");

    /** Trace object for the 'startup' subsystem.  This is used for log
     * messages that need to be generated as a part of server boot. */
    static public Trace startup = trace("startup");

    /** Trace object for the 'timers' subsystem.  This is used for log messages
     * from the clock/timer API. */
    static public Trace timers = trace("timers");

    /** Trace object for the 'trace' subsystem itself. */
    static public Trace trace = trace("trace");

    /**
     * The acceptor this trace object communicates with to output trace log
     * messags.
     */
    private TraceMessageAcceptor myAcceptor;

    /**
     * The subsystem is the group of classes this Trace object applies to.
     */
    String mySubsystem;


    /**
     * Construct a <tt>Trace</tt> object for the given subsystem.  It is legal
     * for the subsystem name to contain blanks.  Things will become confused
     * if it contains a colon, but the code does not check for that.
     *
     * @param subsystem  The name of the subsystem to create a trace object
     *    for.
     */
    private Trace(String subsystem) {
        mySubsystem = subsystem;
        myThreshold = TraceController.theDefaultThreshold;
        myThresholdIsDefaulted = true;
        updateThresholdFlags();
        myAcceptor = TraceController.acceptor();
    }

    /**
     * Obtain the Trace object for a given subsystem.  The Trace object will
     * be manufactured if it does not already exist.
     *
     * @param subsystem  The name of the subsystem of interest.
     *
     * @return a Trace object for the given subsystem.
     */
    public static Trace trace(String subsystem) {
        String key = subsystem.toLowerCase();
        synchronized(theTraces) {
            Trace result = theTraces.get(key);
            if (result == null) {
                result = new Trace(subsystem);
                theTraces.put(key, result);
            }
            return result;
        }
    }

    /**
     * Obtain a Trace object based on another Trace object.  The new trace
     * object will acquire its initial threshold settings based on its parent.
     *
     * @param subSubsystem  A name tag that will be appended to this trace
     *    object's subsystem name.
     *
     * @return a a Trace object derived from this trace object.
     */
    public Trace subTrace(String subSubsystem) {
        Trace result = trace(mySubsystem + "-" + subSubsystem);
        if (result.myThresholdIsDefaulted && !myThresholdIsDefaulted) {
            result.setThreshold(myThreshold);
        }
        return result;
    }

    /**
     * Set the acceptor, i.e., control where the output will go.
     *
     * @param acceptor  The new acceptor to use.
     */
    static void setAcceptor(TraceMessageAcceptor acceptor) {
        for (Trace tr : theTraces.values()) {
            tr.myAcceptor = acceptor;
        }
    }

    /**
     * Set the logging threshold.
     *
     * @param threshold  The new threshold value.
     */
    public void setThreshold(int threshold) {
        if (myThreshold != threshold) {
            myThreshold = threshold;
            updateThresholdFlags();
        }
        myThresholdIsDefaulted = false;
    }

    /**
     * Get the subsystem this trace object applies to.
     *
     * @return the name of this trace object's subsystem.
     */
    String subsystem() {
        return mySubsystem;
    }

    /** 
     * Invoking this method causes this class to be loaded, which causes all
     * the static trace objects to be defined.  In particular, Trace.trace
     * becomes defined.  That's convenient, in that it allows more tracing of
     * the tracing startup itself.
     */
    static void touch() {
    }

    /**
     * Take note of the tracing threshold being changed.  This will set the
     * various control booleans based on the new threshold value.
     */
    private void updateThresholdFlags() {
        verbose = debug = event = usage = warning = error = false;
        switch (myThreshold) {
            /* The order of the cases is significant, fallthrus intentional */
            case Trace.VERBOSE: verbose = true;
            case Trace.DEBUG:   debug   = true;
            case Trace.EVENT:   event   = true;
            case Trace.USAGE:   usage   = true;
            case Trace.WORLD:   world   = true;
            case Trace.WARNING: warning = true;
            case Trace.ERROR:   error   = true;
                break;
            default:
                assert false: "bad case in updateThresholdFlags: " +
                    myThreshold;
        }
    }

    /**
     * Convert an array of bytes into a string suitable for output to a log.
     *
     * Such data is usually UTF-8, or ASCII (which is a subset of UTF-8), but
     * sometimes it is just random binary data.  If we simply use Java's
     * built-in charset conversion, it can produce illegible binary crud in the
     * server log.  Instead we convert it ourselves, rendering printable ASCII
     * bytes as the chars they represent and non-printable bytes as hexadecimal
     * character literal escape sequences.  This will have the downside of
     * sometimes rendering valid Unicode chars funny, but nearly everything we
     * actually care about seeing when debugging is printable ASCII.
     *
     * @param buf  Byte array containing data to be converted.
     * @param offset  Index of start position to convert.
     * @param length  Number of bytes to convert.
     *
     * @return a String rendering the indicated bytes in a legible form
     *    suitable for loggging.
     */
    public static String byteArrayToASCII(byte[] buf, int offset, int length) {
        StringBuilder chars = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            byte b = buf[offset + i];
            if ((' ' <= b && b <= '~') ||
                    b == '\n' || b == '\r' || b == '\t') {
                chars.append((char) b);
            } else {
                chars.append(String.format("\\x%02x", b));
            }
        }
        return chars.toString();
    }

    /**
     * Actually manufacture a debug-style trace message and put it into the
     * log.
     *
     * @param message  The message string
     * @param level  Trace level at which it is being output
     * @param obj  Arbitrary annotation object to go with the message (usually
     *     but not necessarily an exception).
     */
    private void recordDebugMessage(String message, int level, Object obj) {
        StackTraceElement frame;
        try {
            frame = new Exception().getStackTrace()[2];
        } catch (Throwable e) {
            frame = null;
        }

        TraceMessage traceMessage =
            new TraceMessageDebug(mySubsystem, level, frame, message, obj);
        myAcceptor.accept(traceMessage);
    }

    /**
     * Actually manufacture a info-style trace message and put it into the
     * log.
     *
     * @param message  The message string
     * @param level  Trace level at which it is being output
     */
    private void recordInfoMessage(String message, int level) {
        TraceMessage traceMessage =
            new TraceMessageInfo(mySubsystem, level, message);
        myAcceptor.accept(traceMessage);
    }

    /**
     * Actually manufacture an metrics-style trace message and put it into the
     * log.
     *
     * @param type  Message type
     * @param id  Session id number to associate with the message.
     * @param value  Message value
     */
    private void recordMetricsMessage(String type, int id, String value) {
        TraceMessage traceMessage =
            new TraceMessageMetrics(mySubsystem, METRICS, type, id, value);
        myAcceptor.accept(traceMessage);
    }

    /**
     * Exit reporting a fatal error.
     *
     * @param message  The error message to die with.
     */
    public void fatalError(String message) {
        errorm(message);
        TraceController.notifyFatal();
        System.exit(1);
    }

    /**
     * Exit reporting a fatal error, with attached object (usually but not
     * necessarily a {@link Throwable} of some kind).
     *
     * @param message  The error message to die with.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void fatalError(String message, Object obj) {
        errorm(message, obj);
        TraceController.notifyFatal();
        System.exit(1);
    }

    /**
     * Record a {@link Throwable} along with a trace message.<p>
     *
     * To ensure that exceptional conditions are only being ignored for good
     * reason, we adopt the discipline that a caught exception should:<p>
     *
     * 1) be rethrown<br>
     * 2) cause another exception to be thrown instead<br>
     * 3) be ignored, in a traceable way, for some stated reason<p>
     *
     * Only by making #3 explicit can we distinguish it from accidentally
     * ignoring the exception.  An exception should, therefore, only be ignored
     * by asking a {@link Trace} object to shred it.  This request carries a
     * string that justifies allowing the program to continue normally
     * following this event.  As shredded exceptions may be symptoms of bugs,
     * this will enable them to be traced.<p>
     *
     * The reason for the shredding is logged at verbose level.<p>
     *
     * @param ex  The exception to shred.
     * @param reason  A string justifying this.
     */
    public void shred(Throwable ex, String reason) {
        verboseReportException(ex, reason);
    }

    /**
     * Output a string-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, String value) {
        recordMetricsMessage(type, id, "\"" + value + "\"");
    }

    /**
     * Output an integer-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, int value) {
        recordMetricsMessage(type, id, Integer.toString(value));
    }

    /**
     * Output a long-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, long value) {
        recordMetricsMessage(type, id, Long.toString(value));
    }

    /**
     * Output a floating point-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, double value) {
        recordMetricsMessage(type, id, Double.toString(value));
    }

    /**
     * Output an unvalued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     */
    public void metrics(String type, int id) {
        recordMetricsMessage(type, id, null);
    }

    /**
     * Output a boolean-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, boolean value) {
        recordMetricsMessage(type, id, Boolean.toString(value));
    }

    /**
     * Output a JSON object-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, JSONLiteral value) {
        recordMetricsMessage(type, id, value.sendableString());
    }

    /**
     * Output a JSON object-valued metrics message.
     *
     * @param type  Type tag
     * @param id  Session id number to associate with the message.
     * @param value  The value to record.
     */
    public void metrics(String type, int id, JSONObject value) {
        recordMetricsMessage(type, id, value.sendableString());
    }

    /**
     * Output an informational log message at <tt>NOTICE</tt> level (which is
     * unblockable).
     *
     * @param message  The message string
     */
    public void noticei(String message) {
        recordInfoMessage(message, NOTICE);
    }

    /**
     * Output a log message describing a comm message.
     *
     * @param conn  The connection over which the message was sent or received.
     * @param inbound  True if the message was received, false if it was sent
     * @param msg  The message itself that is to be logged
     */
    public void msgi(Object conn, boolean inbound, Object msg) {
        if (event) {
            TraceMessage traceMessage =
                new TraceMessageComm(mySubsystem, MESSAGE, conn.toString(),
                                     inbound, msg.toString());
            myAcceptor.accept(traceMessage);
        }
    }

    /**
     * Output an informational log message at <tt>DEBUG</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void debugi(String message) {
        if (debug) recordInfoMessage(message, DEBUG);
    }

    /**
     * Output a log message at <tt>DEBUG</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void debugm(String message) {
        if (debug) recordDebugMessage(message, DEBUG, null);
    }

    /**
     * Output a log message at <tt>DEBUG</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void debugm(String message, Object obj) {
        if (debug) recordDebugMessage(message, DEBUG, obj);
    }

    /**
     * Log an exception event at <tt>DEBUG</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void debugReportException(Throwable th, String message) {
        if (debug) recordDebugMessage(message, DEBUG, th);
    }


    /**
     * Output an informational log message at <tt>ERROR</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void errori(String message) {
        if (error) recordInfoMessage(message, ERROR);
    }

    /**
     * Output a log message at <tt>ERROR</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void errorm(String message) {
        if (error) recordDebugMessage(message, ERROR, null);
    }

    /**
     * Output a log message at <tt>ERROR</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void errorm(String message, Object obj) {
        if (error) recordDebugMessage(message, ERROR, obj);
    }

    /**
     * Log an exception event at <tt>ERROR</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void errorReportException(Throwable th, String message) {
        if (error) recordDebugMessage(message, ERROR, th);
    }


    /**
     * Output an informational log message at <tt>EVENT</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void eventi(String message) {
        if (event) recordInfoMessage(message, EVENT);
    }

    /**
     * Output a log message at <tt>EVENT</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void eventm(String message) {
        if (event) recordDebugMessage(message, EVENT, null);
    }

    /**
     * Output a log message at <tt>EVENT</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void eventm(String message, Object obj) {
        if (event) recordDebugMessage(message, EVENT, obj);
    }

    /**
     * Log an exception event at <tt>EVENT</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void eventReportException(Throwable th, String message) {
        if (event) recordDebugMessage(message, EVENT, th);
    }


    /**
     * Output an informational log message at <tt>USAGE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void usagei(String message) {
        if (usage) recordInfoMessage(message, USAGE);
    }

    /**
     * Output a log message at <tt>USAGE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void usagem(String message) {
        if (usage) recordDebugMessage(message, USAGE, null);
    }

    /**
     * Output a log message at <tt>USAGE</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void usagem(String message, Object obj) {
        if (usage) recordDebugMessage(message, USAGE, obj);
    }

    /**
     * Log an exception event at <tt>USAGE</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void usageReportException(Throwable th, String message) {
        if (usage) recordDebugMessage(message, USAGE, th);
    }


    /**
     * Output an informational log message at <tt>VERBOSE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void verbosei(String message) {
        if (verbose) recordInfoMessage(message, VERBOSE);
    }

    /**
     * Output a log message at <tt>VERBOSE</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void verbosem(String message) {
        if (verbose) recordDebugMessage(message, VERBOSE, null);
    }

    /**
     * Output a log message at <tt>VERBOSE</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void verbosem(String message, Object obj) {
        if (verbose) recordDebugMessage(message, VERBOSE, obj);
    }

    /**
     * Log an exception event at <tt>VERBOSE</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void verboseReportException(Throwable th, String message) {
        if (verbose) recordDebugMessage(message, VERBOSE, th);
    }


    /**
     * Output an informational log message at <tt>WARNING</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void warningi(String message) {
        if (warning) recordInfoMessage(message, WARNING);
    }

    /**
     * Output a log message at <tt>WARNING</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void warningm(String message) {
        if (warning) recordDebugMessage(message, WARNING, null);
    }

    /**
     * Output a log message at <tt>WARNING</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void warningm(String message, Object obj) {
        if (warning) recordDebugMessage(message, WARNING, obj);
    }

    /**
     * Log an exception event at <tt>WARNING</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void warningReportException(Throwable th, String message) {
        if (warning) recordDebugMessage(message, WARNING, th);
    }


    /**
     * Output an informational log message at <tt>WORLD</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void worldi(String message) {
        if (world) recordInfoMessage(message, WORLD);
    }

    /**
     * Output a log message at <tt>WORLD</tt> level.
     *
     * @param message  The message to write to the log.
     */
    public void worldm(String message) {
        if (world) recordDebugMessage(message, WORLD, null);
    }

    /**
     * Output a log message at <tt>WORLD</tt> level, with attached object.
     *
     * @param message  The message to write to the log.
     * @param obj  Object to report with <tt>message</tt>.
     */
    public void worldm(String message, Object obj) {
        if (world) recordDebugMessage(message, WORLD, obj);
    }

    /**
     * Log an exception event at <tt>WORLD</tt> level.
     *
     * @param th  The exception to log
     * @param message  An explanatory message to accompany the log entry.
     */
    public void worldReportException(Throwable th, String message) {
        if (world) recordDebugMessage(message, WORLD, th);
    }
}
