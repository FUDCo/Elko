package org.elkoserver.util.trace;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * This class describes the file system interface to a log file.  The standard
 * filename format is <tag>.<date>.  The standard versioned filename format is
 * then <tag>.<date>.<sequence>.
 *
 * The entire format may be overridden, the tag may be changed, or the class
 * can be instructed to use System.out.
 *
 * This class is responsible for opening new files.
 */
class TraceLogDescriptor implements Cloneable {
    /*
     * XXX At some point, this might be initialized to some default directory.
     * In Windows, the "current working directory" has a bad habit of hopping
     * around at runtime.
     */
    private static File STARTING_LOG_DIR = new File(".");
    private static String STARTING_LOG_TAG = "log";

    /** The directory in which the log lives. */
    private File myDir = STARTING_LOG_DIR;

    /** The 'tag' is the first component of the log filename. */
    private String myTag = STARTING_LOG_TAG;

    /** Determine whether System.out is used instead of a file. */
    private boolean useStdout = false;

    /** True if the user overrode the standard <tag>.<date> format. */
    private boolean usePersonalFormat = false;

    /** The file being used for the log, if format chosen by user. */
    private String myPersonalFile;

    /** The stream open to the log.  Clients print to this. */
    public PrintStream stream;

    /** The log file to open on rollover. */
    private File myNextFile = null;

    /** The actual file that got opened. */
    private File myFile = null;
    
    /** Log descriptor representing standard output.  Never modify this. */
    static TraceLogDescriptor stdout;

    static {
        stdout = new TraceLogDescriptor();
        stdout.usePersonalFormat = false;
        stdout.useStdout = true;
    }


    /** 
     * Get the file to use as as the next version of a file.  Stdout is never
     * versioned, so useStdout should be false.
     *
     * @param file  The file that we desire a version name for.
     * @param clashAction  How the versioned name is to be determined.
     *    VA_ADD means a file with the next highest sequence number.
     *    VA_OVERWRITE means a file with the smallest sequence number.
     */
    private File versionFile(File file, int clashAction) {
        if (clashAction == TraceLog.VA_ADD) {
            return new TraceVersionNamer(file).nextAvailableVersion();
        } else if (clashAction == TraceLog.VA_OVERWRITE) {
            return new TraceVersionNamer(file).firstVersion();
        } else { 
            throw new Error("Bad clashAction " + clashAction);
        }
    }

    /**
     * A clone of a TraceLogDescriptor is one that, when startUsing() is
     * called, will use the same descriptor, be it a file or System.out.  The
     * clone is not inUse(), even if what it was cloned from was.
     */
    protected Object clone() {
        try {
            TraceLogDescriptor cl = (TraceLogDescriptor) super.clone();
            cl.stream = null;
            return cl;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /** 
     * Figure out what the next log file name will be when rolling over log
     * files.  This method is used because the current log file will be closed
     * before the next one is opened.  Predetermining the file here enables ust
     * to write some information about the new file into the old one.
     */
    void prepareToRollover(int clashAction) {
        if (useStdout) {
            myNextFile = null;
            return;
        }
        
        myNextFile = desiredLogFile();
        if (!myNextFile.exists()) {
            Trace.trace.worldi("Logging will continue on " + myNextFile);
        } else {
            myNextFile = versionFile(myNextFile, clashAction);
            Trace.trace.worldi("The log file will be backed up as " +
                               myNextFile);
        }
    }

    /** 
     * Given this object's configuration state, construct the filename the user
     * wants.  It is a program error to call this routine if the user wants
     * System.out, not a file.
     */
    private File desiredLogFile() {
        if (usePersonalFormat) {
            if ((new File(myPersonalFile)).isAbsolute()) {
                return new File(myPersonalFile);
            } else {
                return new File(myDir, myPersonalFile);
            }
        } else {
            long timestamp = System.currentTimeMillis();
            return new File(myDir, myTag + "." +
                            TraceDate.terseCompleteDateString(timestamp));
        }
    }

    /**
     * Two TraceLogDescriptors are equal iff they refer to the same (canonical)
     * file.
     */
    public boolean equals(TraceLogDescriptor other) {
        return printName().equals(other.printName());
    }

    boolean inUse() { 
        return stream != null;
    }

    /** 
     * Return a name of this descriptor, suitable for printing.  System.out is
     * named "standard output".  Real files are named by their canonical
     * pathname (surrounded by single quotes).
     *
     * Note that the printname may be the absolute pathname if the canonical
     * path could not be discovered (which could happen if the file does not
     * exist.)
     */
    String printName() {
        if (useStdout) {
            return "standard output";
        } else if (myFile == null) {
            return "unknown";
        } else {
            String canonical;
            try {
                canonical = myFile.getCanonicalPath();
            } catch (IOException e) {
                /* The canonical path was undiscoverable.  Punt by returning
                   the absolute pathname. */
                canonical = myFile.getAbsolutePath();
            }
            if (canonical == null) {
                /* Quoth the java language spec: "The canonical form of a
                   pathname of a nonexistent file may not be defined." What
                   happens in that case is ALSO not defined.  Null seems like a
                   possibility. */
                canonical = myFile.getAbsolutePath();
            }
             
            return "'" + canonical + "'";
        }
    }

    /**
     * The user wishes to use a directory component different than the default.
     * The file used is unchanged.
     */
    void setDir(String value) {
        useStdout = false;
        /* Don't change value of usePersonalFormat, as the directory is
           independent of the filename format. */
        myDir = new File(value);
        Trace.trace.worldi("Log directory will be changed to '" +
                            value + "'.");
        if (!myDir.isDirectory()) {
            Trace.trace.warningi("The log directory was set to '"
                + value + "', which is not currently a directory.");
        }
    }

    /**
     * If the argument is "-", standard output is used.  If the argument is
     * something else, that becomes the complete filename, overriding the tag,
     * eliminating use of the date/time field, and not using the default
     * extension.  It does not affect the directory the file is placed in.
     */
    void setName(String value) { 
        if (value.equals("-")) {
            useStdout = true;
            usePersonalFormat = false;
            Trace.trace.worldi("Log destination set to standard output.");
        } else {
            useStdout = false;
            usePersonalFormat = true;
            myPersonalFile = value;
            Trace.trace.worldi("Log destination will be changed to " +
                "file '" + myPersonalFile + "'.");
        }
    }

    /**
     * The tag is the initial part of the standard filename.  Setting this
     * implies that the date should be included in the filename.
     */
    void setTag(String value) {
        useStdout = false;
        usePersonalFormat = false;
        myTag = value;
        Trace.trace.eventi("Log tag set to '" + value + "'.");
    }

    /**
     * Enables this LogDescriptor for use.  Most obvious effect is that
     * 'stream' is initialized.  
     *
     * @param clashAction Determines what to do if the target logfile already
     *    exists.  The two options are VA_ADD (to add a new version file) or
     *    VA_OVERWRITE (to overwrite an existing one).  VA_IRRELEVANT should be
     *    used when the destination is <em>known</em> to be standard output,
     *    which never clashes.
     *
     * @throws Exception if a logfile could not be opened.  The contents of the
     *    exception are irrelevant, as this method logs the problem.
     */
    void startUsing(int clashAction, TraceLogDescriptor previous)
        throws Exception
    {
        if (useStdout) {
           stream = System.out;
           myFile = null;
           return;
        }

        File nextFile;
        if (previous != null) {
            nextFile = previous.myNextFile;
        } else {
            nextFile = desiredLogFile();
        }

        if (nextFile.isDirectory()) {
            Trace.trace.errorm("Attempt to open directory " +
                               nextFile + " as a logfile failed.");
            throw new IOException("opening directory as a logfile");
        }

        try {
            stream = new PrintStream(new FileOutputStream(nextFile));
            myFile = nextFile;
            myNextFile = null;
        } catch (SecurityException e) {
            Trace.trace.errorm(
                "Security exception when opening new trace file '" + 
                nextFile + "'.");
            throw e;
        } catch (FileNotFoundException e) {
            Trace.trace.errorm(
                "Could not open new trace file '" + 
                nextFile + "'.");
            throw e;
        }
    }

    /**
     * Cease using this LogDescriptor.  The most obvious effect is that
     * 'stream' is now null.  Behind the scenes, any open file is closed.
     * You can alternate stopUsing() and startUsing() an arbitrary number of
     * times.  
     */
    void stopUsing() {
        if (stream != System.out) {
            Trace.trace.eventi("Closing " + printName());
            stream.close();
        }
        stream = null;
        myFile = null;
    }
}
