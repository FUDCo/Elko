package org.elkoserver.util.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The single trace controller which manages the operation of the tracing
 * system.
 */
public class TraceController {
    private static int STARTING_LOG_THRESHOLD = Trace.WARNING;

    /**
     * Trace threshold that applies to subsystems that haven't been given
     * specific values.
     */
    static /*private*/ int theDefaultThreshold;

    /** Have we already been initialized? */
    static private boolean theStarted = false;

    /** The acceptor, for use by Traces. */
    static private TraceMessageAcceptor theAcceptor;

    /**
     * Synchronized static methods are prone to deadlocks in Sun's JVM.  This
     * avoids the problem.  Initialized in the class initializer to make sure
     * it's available before the first trace object is used.
     */
    static private Object theSynchronizationObject;

    static {
        /* Initialize component objects.  Note that these constructors must not
           call any trace functions, since the acceptors and thresholds haven't
           been set up.  The component objects can't be initialized after
           Trace, because it makes use of them.
        */
        theSynchronizationObject = new Object();
        theAcceptor = new TraceLog(); /* disk log as default */
        theDefaultThreshold = STARTING_LOG_THRESHOLD;

        /* Load Trace.class to define trace.trace().  Otherwise, it only gets
           loaded when the first client refers to it.  It's convenient to load
           it as early as possible so that the tracing subsystem's startup can
           itself be traced.  It cannot be loaded earlier than this point. */
        Trace.touch();
    }

    /**
     * Suppress the Miranda constructor.
     */
    private TraceController() {
    }

    /**
     * Get the current trace message acceptor.
     */
    static TraceMessageAcceptor acceptor() {
        return theAcceptor;
    }

    /**
     * Change the default tracing threshold.
     */
    static private void changeDefaultThreshold(int newThreshold) {
        theDefaultThreshold = newThreshold;
        
        Trace.trace.eventi("The new default threshold for log is " +
                           TraceLevelTranslator.terse(newThreshold));
    }

    /** 
     * Change a specific Trace to have its own trace priority threshold OR
     * change it to resume tracking the default.
     */
    static private void changeSubsystemThreshold(String subsystem,
                                                 String value)
    {
        Trace tr = Trace.trace(subsystem);
        if (value.equalsIgnoreCase(TraceLog.DEFAULT_NAME)) {
            tr.setThreshold(theDefaultThreshold);
        } else {
            tr.setThreshold(TraceLevelTranslator.toInt(value));
        }
    }

    /**
     * Notify a log that a fatal error has happened.
     */
    static void notifyFatal() {
        Trace.trace.errorm("A fatal error has been reported");
    }

    /**
     * Set the acceptor that will be used to actually handle trace events.
     *
     * @param acceptor  The new acceptor.
     */
    static public void setAcceptor(TraceMessageAcceptor acceptor) {
        theAcceptor = acceptor;
        Trace.setAcceptor(theAcceptor);
    }

    /**
     * Set the trace control properties from a given set of properties.<p>
     *
     * IMPORTANT:  The properties are processed in an unpredictable order.
     *
     * @param props
     */
    static public void setProperties(Properties props) {
        for (Map.Entry<Object,Object> entry : props.entrySet()) {
            setProperty((String) entry.getKey(), (String) entry.getValue());
        }
    }

    /** 
     * Set one of the trace control properties.<p>
     *
     * If the given key names a tracing property, process its value.  Note that
     * it is not an error for the key to have nothing to do with tracing; in
     * that case, it's ignored.  It <i>is</i> an error for the value to be
     * <tt>null</tt>.
     *
     * @param key  The name of the property to set.
     * @param value  The value to set it to, if it is a trace control property.
     */
    static public void setProperty(String key, String value) {
        /* Note: synchronization is the responsibility of the objects whose
           properties are being changed. */

        assert value != null : "Trace property value cannot be null.";
        key = key.trim();
        value = value.trim();
        Trace.trace.debugm("Setting property " + key + " to value " + value);
        try {
            boolean logProp = false;
            String originalKey = key;
            String lowerKey = key.toLowerCase();
            String afterUnderbar = null;

            if (lowerKey.startsWith("trace_")) {
                logProp = true;
                afterUnderbar = originalKey.substring(6);
            } else if (lowerKey.startsWith("tracelog_")) {
                logProp = true;
                afterUnderbar = originalKey.substring(9);
            }
            
            if (logProp) {
                if (afterUnderbar.equalsIgnoreCase(TraceLog.DEFAULT_NAME)) {
                    changeDefaultThreshold(TraceLevelTranslator.toInt(value));
                } else if (!theAcceptor.setConfiguration(afterUnderbar,
                                                         value)) {
                    changeSubsystemThreshold(afterUnderbar, value);
                }
            }
        } catch (IllegalArgumentException e) {
            Trace.trace.shred(e, "The exception has already been logged.");
        }
        
        /* Other properties are ignored, because this method may be called from
           setProperties, which is given a whole mess of properties, some
           irrelevant to Trace. */
    }

    /**
     * Start the operation of the trace system.  Prior to this call, {@link
     * Trace} objects may be obtained and messages may be sent to them.
     * However, the messages will be queued up until this routine is called.
     * (Note that the messages will be governed by the default thresholds.)<p>
     *
     * Applications should not need to call this method, since normally it is
     * called automatically by the server boot class (e.g., {@link
     * org.elkoserver.foundation.boot.Boot}).
     *
     * @param props  The initial set of properties provided by the user.  They
     *    override the defaults.  They may be changed later.
     */
    static public void start(Properties props) {
        if (theStarted) {
            Trace.trace.errorm(
                "The tracing system is being started for a second time.\n" +
                "Ignoring the second start.");
            return;
        }       

        theStarted = true;
        Trace.trace.usagem("Tracing system being started.");

        if (!props.containsKey("tracelog_write")) {
            props.put("tracelog_write", "true");
        }
        
        if (!props.containsKey("tracelog_name") && 
                !props.containsKey("tracelog_dir") && 
                !props.containsKey("tracelog_tag")) {
            props.put("tracelog_name", "-");
        }

        setProperties(props);
        theAcceptor.setupIsComplete();
        new TraceExceptionNoticer();
    }
}
