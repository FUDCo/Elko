package org.elkoserver.util.trace;

import java.io.File;

/**
 * This class knows how to construct sequenced output Files for given Files.
 *
 * This class constructs version names by appending sequence numbers to the
 * original name.
 */
class TraceVersionNamer { 
    /** The file for which a version number is being created. */
    private File myFile;

    /** The 'filename' part of the given file, sans directory. */
    private String myName;

    /** A directory that contains the given file. */
    private String myDir;

    /** The part of the filename that precedes a sequence number. */
    private String myBasename;

    /**
     * Create a TraceVersionNamer from the given file.  The file object must
     * have a directory and name component.  It may be absolute or relative.  
     */
    TraceVersionNamer(File file) {
        myFile = file;
        myName = myFile.getName();
        myDir = myFile.getParent();
        myBasename = myName + ".";
        Trace.trace.debugm("Finding next version of " + myFile + "(" + 
                           myDir + " " + myName + " " + myBasename + ")");
    }

    /**
     * Create a version file name, given a sequence number.
     */
    private File constructVersion(int sequence) {
        return new File(myDir, myBasename + String.format("%03d", sequence));
    }

    /**
     * The backup file with sequence number 0.  Doesn't matter if it exists.
     */
    File firstVersion() {
        return constructVersion(0);
    }

    /**
     * Return a sequence number, if the given filename contains one.  If it
     * does not contain one, return -1.  Do not call this method unless
     * mightBeVersion has approved the filename.
     */
    private int getSeq(String filename) {
        String possibleSeqString = filename.substring(myBasename.length());

        try { 
            return Integer.parseInt(possibleSeqString);
        } catch (NumberFormatException e) {
            Trace.trace.shred(e, filename + " has invalid sequence number");
            return -1; 
        }
    }

    /** 
     * True iff the filename is of a format that could be a backup version of
     * the original file.  It remains to be determined whether it truly
     * contains a sequence number.
     *
     * In the interest of platform independencee, the check is case-
     * insensitive.  This obeys Windows conventions about what "same files"
     * are, not Unix conventions.
     *
     * @param filename a filename, not including any directory part.
     */
    private boolean mightBeVersion(String filename) {
        int minlen = myBasename.length() + 1;
        return filename.length() >= minlen &&
            filename.toLowerCase().startsWith(myBasename.toLowerCase());
    }

    /**
     * Return the next file in the sequence <foo>, <foo>.0, <foo>.1, etc.
     * Subclasses will have their own name-construction rules.
     */
    File nextAvailableVersion() {
        int highestSeq = -1;
        String[] files = (new File(myDir)).list();

        if (files == null) {
            /* We were asked for a file in a nonexistent directory.  It's safe
               to assume that there are no clashing names. */
            return firstVersion();
        }
                                       
        for (String file : files) {
            if (mightBeVersion(file)) {
                int possibleSeq = getSeq(file);
                if (possibleSeq < 0) {
                    Trace.trace.verbosem(file + " has no sequence number");
                } else if (possibleSeq <= highestSeq) {
                    Trace.trace.verbosem(file + " is too low");
                } else {
                    highestSeq = possibleSeq;
                    Trace.trace.verbosem(highestSeq + " is the best so far");
                }
            } else {
                Trace.trace.verbosem(file + " is not in version file format");
            }
        }
        File result = constructVersion(highestSeq + 1);
        Trace.trace.eventi("Backup version for " + myFile + " is " + result);
        return result;
    }
}
