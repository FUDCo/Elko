package org.elkoserver.util.trace;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * This class manages dumping of messages to the semi-permanent on-disk log.
 * Will queue messages until it's pointed at a log file or stdout.  Messages
 * will be redirected to stdout if a given logfile can't be opened.
 */
class TraceLog implements TraceMessageAcceptor {

    /* Trace log file defaults */
    private static final long STARTING_LOG_SIZE_THRESHOLD = 500000;
    private static final long SMALLEST_LOG_SIZE_THRESHOLD = 1000;

    /* Behaviors when opening files that already exist */
    static final int VA_IRRELEVANT = -1; /* When opening stdout */
    static final int VA_OVERWRITE = 0;   /* Empty: overwrite existing */
    static final int VA_ADD = 1;         /* Rollover: add a new file */
    private static final int STARTING_LOG_VERSION_ACTION = VA_ADD;

    static final String DEFAULT_NAME = "default";

    /** What to do with full or existing log files: rollover or empty. */
    private int myVersionAction = STARTING_LOG_VERSION_ACTION;

    /** Flag controlling whether a log file should be written at all. */
    private boolean amWriteEnabled = false;

    /** Flag controlling whether log messages should be sent to stderr. */
    private boolean logToStderr = false;

    /** Log file size above which the file will be rolled over, in chars. */
    private long myMaxSize = STARTING_LOG_SIZE_THRESHOLD;

    /** Flag that max size was explicitly set rather than defaulted. */
    private boolean amMaxSizeSet = false;

    /** Number of characters in the current log file. */
    private long myCurrentSize;

    /** Frequency with which log files are rolled over, in milliseconds. */
    private long myRolloverFrequency = 0;

    /** Time of next scheduled log file rollover, or 0 if rollover is off */
    private long myNextRolloverTime = 0;

    /** Log to which messages are currently flowing, or null if none yet. */
    private TraceLogDescriptor myCurrent;

    /**
     * The user can change the characteristics of this log descriptor, then
     * redirect the log to it.  Characteristics are changed via properties like
     * "tracelog_tag".  Redirection is done via "tracelog_reopen".  
     */
    private TraceLogDescriptor myPending = new TraceLogDescriptor();

    /** True if all the initialization properties have been processed. */
    private boolean mySetupComplete = false;

    /** Buffer for building log message strings in. */
    private StringBuilder myStringBuffer;

    /** Queue for messages prior to log init and while switching log files. */
    private List<TraceMessage> myQueuedMessages;

    private static final int LINE_SEPARATOR_LENGTH = 
        System.getProperty("line.separator").length();

    /** 
     * Constructor.  Queue messages until setup is complete.
     */
    TraceLog() {
        /*
          DANGER:  This constructor must be called as part of static
          initialization of TraceController.  Until that initialization is
          done, Trace should not be loaded.  Therefore, nothing in this
          constructor should directly or indirectly use a tracing function.
        */
        myStringBuffer = new StringBuilder(200);
        startQueuing();
    }

    /**
     * Accept a message for the log.  It will be discarded if both writing
     * and the queue are turned off.
     */
    public synchronized void accept(TraceMessage message) {
        if (isAcceptingMessages()) {
            if (isQueuing()) {
                myQueuedMessages.add(message);
            } else {
                outputMessage(message);
            }
        }
    }

    /**
     * Take a message and actually output it to the log.  In particular, the
     * queue of pending messages is bypassed, because this method is used in
     * the process of draining that queue.
     */
    private void outputMessage(TraceMessage message) {
        message.stringify(myStringBuffer);
        String output = myStringBuffer.toString();
        if (logToStderr) {
            System.err.println(output);
        } else {
            myCurrent.stream.println(output);
            /* Note: there's little point in checking for an output error.  We
               can't put the trace in the log, and there's little chance the user
               would see it in the trace buffer.  So we ignore it, with regret. */

            myCurrentSize += output.length() + LINE_SEPARATOR_LENGTH;
            if (myCurrentSize > myMaxSize) {
                rolloverLogFile("This log is full.");
            } else if (myNextRolloverTime != 0 &&
                       myNextRolloverTime < message.timestamp()) {
                do {
                    myNextRolloverTime += myRolloverFrequency;
                } while (myNextRolloverTime < message.timestamp());
                rolloverLogFile("The time has come for a new log file.");
            }
        }
    }

    /**
     * Call to initialize a log when logging is just beginning (or resuming
     * after having been turned off).  There is no current log, so nothing is
     * written to it.  If the pending log cannot be opened, standard output is
     * used as the log.  In any case, the queue is drained just before the
     * method returns.
     */
    private void beginLogging() {
        try {
            /* Rename any existing file */
            myPending.startUsing(myVersionAction, null);
        } catch (Exception e) {
            /* Couldn't open the log file.  Bail to stdout. */
            Trace.trace.shred(e, "Exception has already been logged.");

            myCurrent = TraceLogDescriptor.stdout;
            try { 
                myCurrent.startUsing(VA_IRRELEVANT, null);
            } catch (Exception ignore) {
                assert false: "Exceptions shouldn't happen opening stdout.";
            }
            drainQueue();
            return;
        }
        myCurrent = myPending;
        Trace.trace.worldi("Logging begins on " + myCurrent.printName() + ".");
        myPending = (TraceLogDescriptor) myCurrent.clone();
        myCurrentSize = 0;
        drainQueue();
    }

    /**
     * Change how a full logfile handles its version files.  "one" or "1" means
     * that there will be at most one file, which will be overwritten if
     * needed.  "many" means a new versioned file with a new name should be
     * created each time the base file fills up.  Has effect when the next log
     * file fills up.
     */
    private void changeVersionFileHandling(String newBehavior) {
        if (newBehavior.equalsIgnoreCase("one") || newBehavior.equals("1")) {
            Trace.trace.eventi("Log files will be overwritten.");
            myVersionAction = VA_OVERWRITE;
        } else if (newBehavior.equalsIgnoreCase("many")) {
            Trace.trace.eventi("New version files will always be created.");
            myVersionAction = VA_ADD;
        } else {
            Trace.trace.errori(
                "tracelog_versions property was given unknown value '" +
                newBehavior + "'.");
        }
    }

    /**
     * Change the default directory in which logfiles live.  Has effect only
     * when a new logfile is opened.
     */
    private void changeDir(String value) {
        myPending.setDir(value);
    }

    /**
     * Explicitly set the name of the next logfile to open.  Overrides the
     * effect of "tracelog_dir" only if the given name is absolute.  Has effect
     * only when a new logfile is opened.
     */
    private void changeName(String value) {
        myPending.setName(value);
    }

    /**
     * Change the time-based log file rollover policy.  By default, log files
     * are only rolled over when they reach some size threshold, but by setting
     * this you can also make them rollover based on the clock.
     *
     * @param value  Rollover policy.  Valid values are "weekly", "daily",
     *    "hourly", "none", or an integer number that expresses the rollover
     *    frequency in minutes.
     */
    private void changeRollover(String value) {
        int freq = 0;
        Calendar startCal = Calendar.getInstance();
        if (value.equalsIgnoreCase("weekly")) {
            freq = 7 * 24 * 60;
            startCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);
        } else if (value.equalsIgnoreCase("daily")) {
            freq = 24 * 60;
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);
        } else if (value.equalsIgnoreCase("hourly")) {
            freq = 60;
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);
        } else if (value.equalsIgnoreCase("none")) {
            freq = 0;
        } else {
            try {
                freq = Integer.parseInt(value);
                if (freq < 1) {
                    Trace.trace.errori("Log size rollover value " + value +
                                       "too small (minimum is 1 minute)");
                    freq = 0;
                } else {
                    int minute = startCal.get(Calendar.MINUTE);
                    startCal.set(Calendar.MINUTE, (minute / freq) * freq);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                }
            } catch (NumberFormatException e) {
                Trace.trace.errori("Invalid log size rollover value '" +
                                   value + "'.");
            }
        }
        if (freq != 0) {
            myRolloverFrequency = freq * 60 * 1000;
            if (!amMaxSizeSet) {
                myMaxSize = Long.MAX_VALUE;
            }
            long startTime = startCal.getTimeInMillis();
            myNextRolloverTime = startTime + myRolloverFrequency;
        } else {
            myNextRolloverTime = 0;
        }
    }

    /**
     * Change the new maximum allowable size for a logfile.  Has effect on the
     * current logfile.  Note that the trace system does not prevent the log
     * from exceeding this size; it only opens a new log file as soon as it
     * does.
     */
    private void changeSize(String value) {
        long newSize;
        if (value.equalsIgnoreCase(DEFAULT_NAME)) {
            newSize = STARTING_LOG_SIZE_THRESHOLD; 
        } else if (value.equalsIgnoreCase("unlimited")) {
            newSize = Long.MAX_VALUE;
        } else try { 
            newSize = Long.parseLong(value);
        } catch (NumberFormatException e) {
            Trace.trace.errori(
                "Log size cannot be changed to illegal value '" +
                value + "'.");
            newSize = myMaxSize;  /* leave unchanged. */
        }

        if (newSize < SMALLEST_LOG_SIZE_THRESHOLD) {
            Trace.trace.errori(
                value + " is too small a threshold size for the log. "
                + "Try " + SMALLEST_LOG_SIZE_THRESHOLD + ".");
            newSize = myMaxSize;
        }

        amMaxSizeSet = true;
        myMaxSize = newSize;
        if (myCurrentSize > myMaxSize) {
            rolloverLogFile("This log is full.");
        }
    }

    /**
     * Change the 'tag' (base of filename) that logfiles have.  Has effect only
     * when a new logfile is opened.
     */
    private void changeTag(String value) {
        myPending.setTag(value);
    }

    /**
     * Sets the tracing log to output to stderr if the user so desires.
     */
    private void changeLogToStderr(String value) {
        logToStderr = Boolean.parseBoolean(value);
    }

    /**
     * The meaning of changeWrite is complicated.  Here are the cases when it's
     * used to turn writing ON.
     *
     * If setup is still in progress, the state variable 'amWriteEnabled' is
     * used to note that logging should begin when setupIsComplete() is called.
     *
     * If setup is complete, logging should begin immediately.  If logging has
     * already begun, this is a no-op.
     *
     * Here are the cases for turning writing OFF.
     *
     * If setup is not complete, the state variable 'amWriteEnabled' informs
     * setupIsComplete() that logging should not begin.
     *
     * If setup is complete, logging is stopped.  However, if it was already
     * stopped, the call is a no-op.
     *
     * There would be some merit in having a state machine implement all this.
     */
    private void changeWrite(String value) {
        if (value.equalsIgnoreCase("true")) {
            if (amWriteEnabled) { 
                Trace.trace.warningi("Log writing enabled twice in a row.");
            } else {
                amWriteEnabled = true;
                startQueuing(); /* it's ok if the queue already started. */
                Trace.trace.eventi("Logging is enabled.");
                if (mySetupComplete) {
                    beginLogging();
                } 
            }
        } else if (value.equalsIgnoreCase("false")) {
            if (!amWriteEnabled) {
                Trace.trace.warningi("Log writing disabled twice in a row.");
            } else {
                Trace.trace.eventi("Logging disabled.");
                drainQueue(); /* either write messages or discard them */
                amWriteEnabled = false;
                if (mySetupComplete) { 
                    myCurrent.stopUsing();
                    myCurrent = null;
                } else { 
                    assert myCurrent == null;
                }
            }
        } else {
            Trace.trace.errori("tracelog_write property was given value '" +
                value + "'.");
        }
    }

    /**
     * Deal with messages accumulated in the queue.  If the log is turned on
     * (amWriteEnabled is true), they are written.  Otherwise, they are
     * discarded.  It is safe to call this routine without knowing whether
     * queuing is in progress.
     */
    private void drainQueue() {
        if (amWriteEnabled && isQueuing()) {
            List<TraceMessage> queueToDrain = myQueuedMessages;
            myQueuedMessages = null;
            for (TraceMessage message : queueToDrain) {
                outputMessage(message);
            }
        } 
        myQueuedMessages = null;
    }

    /** 
     * Call when the logfile fills up or reaches rollover time.  Reopens the
     * same log file.
     *
     * Standard output can never fill up, so this routine is a no-op when the
     * current size of text sent to standard out exceeds the maximum, except
     * that the current size is reset to zero.
     */
    private void rolloverLogFile(String why) {
        /* Preemptively set the log size back to zero.  This allows log
           messages about the fullness of the log to be placed into the log,
           without getting into an infinite recursion.
        */
        myCurrentSize = 0;
        if (myCurrent.stream != System.out) {
            Trace.trace.worldi(why);
            shutdownAndSwap();
        }
    }

    /**
     * Call to switch to a log when another - with a different name - is
     * currently being used.  If the pending log cannot be opened, the current
     * log continues to be used.  
     *
     * Before the old log is closed, a WORLD message is logged, directing the
     * reader to the new log.  Trace messages may be queued while the swap is
     * happening, but the queue is drained before the method returns.
     *
     * This routine is never called when the logfile fills - it's only used
     * when explicitly reopening a log file.  (tracelog_reopen=true).
     */
    private void hotSwap() {
        /* Finish the old log with a pointer to the new. */
        Trace.trace.worldi("Logging ends.");
        Trace.trace.worldi("Logging will continue on " +
                           myPending.printName() + ".");

        startQueuing(); /* further messages should go to the new log. */
        try {
            /* rename an existing file, since it is not an earlier version of
               the new name we're using. */
            myPending.startUsing(myVersionAction, null);
        } catch (Exception e) {
            Trace.trace.shred(e, "Exception has already been logged.");
            /* continue using current. */
            drainQueue();
            return;
        }
        /* Stash old log name to print in new log. */
        String lastLog = myCurrent.printName();

        myCurrent.stopUsing();
        myCurrent = myPending;
        Trace.trace.worldi("Logging begins on " + myCurrent.printName() + ".");
        Trace.trace.worldi("Previous log was " + lastLog + ".");

        myCurrentSize = 0;
        myPending = (TraceLogDescriptor) myCurrent.clone();
        drainQueue();
    }

    /**
     * Test if this log is accepting messages.
     *
     * The log accepts messages if the "tracelog_write" property was set.
     * Before setup is completed, it also accepts and queues up messages.  When
     * setup is complete, it either posts or discards those queued messages,
     * depending on what the user wants.
     *
     * Queuing also happens transitorily while logs are being switched.
     *
     * @return true if this log is accepting messages, false if it is
     *    discarding them.
     */
    private boolean isAcceptingMessages() { 
        return amWriteEnabled || isQueuing();
    }

    /**
     * Test if this log is queueing messages.
     *
     * The log queues messages during its startup time and while switching
     * between open log files.
     *
     * @return true if this log is currently queueing messages.
     */
    private boolean isQueuing() {
        return myQueuedMessages != null;
    }

    /**
     * The gist of this routine is that it shuts down the current log and
     * reopens a new one (possibly with the same name, possibly with a
     * different name).  There are some special cases, because this routine
     * could be called before setup is complete (though using tracelog_reopen
     * in the initial Properties is deprecated).
     *
     * It's called before setup is complete and writing is not enabled.  The
     * behavior is the same as tracelog_write [the preferred interface].
     *
     * It's called before setup is complete and writing is enabled.  The effect
     * is that of calling tracelog_write twice (a warning).
     *
     * It's called after setup is complete and writing is not enabled.  The
     * behavior is the same as calling tracelog_write [again, the preferred
     * interface, because you're not "reopening" anything].
     *
     * It's called after setup is complete and writing is enabled.  This is the
     * way it's supposed to be used.  The current log is closed and the pending
     * log is opened.
     */
    private void reopen(String ignored) {
        if (!amWriteEnabled || !mySetupComplete) {
            changeWrite("true");
        } else if (myPending.equals(myCurrent)) {
            shutdownAndSwap();
        } else { 
            hotSwap();
        }
    }

    /**
     * Modify the acceptor configuration based on a property setting.  Property
     * names here are not true property names but property names with the
     * "trace_" or "tracelog_" prefix stripped off.
     *
     * @param name  Property name
     * @param value   Property value
     *
     * @return true if the property was recognized and handled, false if not
     */
    public synchronized boolean setConfiguration(String name, String value) {
        if (name.equalsIgnoreCase("write")) {
            changeWrite(value);
        } else if (name.equalsIgnoreCase("dir")) {
            changeDir(value);
        } else if (name.equalsIgnoreCase("tag")) {
            changeTag(value);
        } else if (name.equalsIgnoreCase("name")) {
            changeName(value);
        } else if (name.equalsIgnoreCase("size")) {
            changeSize(value);
        } else if (name.equalsIgnoreCase("rollover")) {
            changeRollover(value);
        } else if (name.equalsIgnoreCase("versions")) {
            changeVersionFileHandling(value);
        } else if (name.equalsIgnoreCase("stderr")) {
            changeLogToStderr(value);
        } else if (name.equalsIgnoreCase("reopen")) {
            reopen(value);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Call this only after all properties have been processed.  It begins
     * logging, but only if tracelog_write or tracelog_reopen have been used,
     * or if the default behavior is to write.
     */
    public synchronized void setupIsComplete() {
        Trace.trace.eventi("Logging is being started.");
        if (amWriteEnabled) {
            beginLogging();
        }
        drainQueue();
        mySetupComplete = true;
    }

    /**
     * Call to initialize a log when the same file is already open.  If the
     * pending log cannot be opened, standard output is used.
     *
     * Before the old log is closed, a WORLD message is logged, directing the
     * reader to the new log.  Trace messages may be queued while the swap is
     * happening, but the queue is drained before the method returns.
     *
     * This routine can be called to version a full logfile, or to explicitly
     * reopen the same logfile (via tracelog_reopen=true).
     */
    private void shutdownAndSwap() {
        /* In the old log, say what will happen.  Can't log it while it's
           happening, because that all goes to the new log.
        */
        myCurrent.prepareToRollover(myVersionAction);

        /* Stash old log name.  This is used if reopening fails and further
           logging is blurted to stdout.
        */
        String lastLog = myCurrent.printName();

        myCurrent.stopUsing();

        startQueuing(); /* further messages should go to the new log. */

        try {
            myPending.startUsing(myVersionAction, myCurrent);
        } catch (Exception e) {
            Trace.trace.shred(e, "Exception has already been logged.");
            myCurrent = TraceLogDescriptor.stdout;
            myCurrentSize = 0;
            try { 
                myCurrent.startUsing(VA_IRRELEVANT, null);
            } catch (Exception ignore) {
                assert false: "No exceptions when opening stdout.";
            }
            drainQueue();
            Trace.trace.worldi("Previous log was " + lastLog + ".");
            return;
        }
 
        myCurrent = myPending;
        myCurrentSize = 0;
        Trace.trace.worldi("Logging continues on " + myCurrent.printName() +
                           ".");
        Trace.trace.worldi("Previous log was " + lastLog + ".");
        myPending = (TraceLogDescriptor) myCurrent.clone();
        drainQueue();
    }

    /** 
     * Redirect trace messages to a queue.  Used while switching to a new log
     * file, or before setup is complete.
     *
     * It is harmless to call this routine twice.
     */
    private void startQueuing() {
        /* NOTE: trace messages must not be generated by this routine, because
           it's called from the constructor.
        */
        if (!isQueuing()) {
            myQueuedMessages = new LinkedList<TraceMessage>();
        } 
    }
}
