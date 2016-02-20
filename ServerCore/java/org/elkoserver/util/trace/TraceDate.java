package org.elkoserver.util.trace;

import java.util.Calendar;

/** 
 * A version of the Date class that provides some more convenient reporting
 * functions.
 */
class TraceDate {

    /**
     * Constructor
     */
    private TraceDate () {
    }

    /**
     * Return time in form YYYYMMDDHHMMSS.  This is a terse sortable time.
     * Fields are zero-padded as needed.
     */
    static String terseCompleteDateString(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        String retval = String.valueOf(cal.get(Calendar.YEAR));
        retval +=
            zeroFill(cal.get(Calendar.MONTH)+1) +
            zeroFill(cal.get(Calendar.DAY_OF_MONTH)) +
            zeroFill(cal.get(Calendar.HOUR_OF_DAY)) +
            zeroFill(cal.get(Calendar.MINUTE)) +
            zeroFill(cal.get(Calendar.SECOND));
        return retval;
    }

    /**
     * Convert an int to a string, filling on the left with zeros as needed to
     * ensure that it is at least two digits.
     */
    private static String zeroFill(int number) {
        if (number <= 9) {
            return "0" + String.valueOf(number);
        } else {
            return String.valueOf(number);
        }
    }
}
