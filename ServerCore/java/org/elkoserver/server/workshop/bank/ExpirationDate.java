package org.elkoserver.server.workshop.bank;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;

/**
 * Object representing the date and time at which a key or encumbrance will
 * cease to be valid.
 */
class ExpirationDate implements Comparable, Encodable {
    /** Millisecond clock time at which this expiration happens. */
    private long myTime;

    /** Singleton DateFormat object for parsing timestamp literals. */
    private static DateFormat theDateFmt =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
    static {
        theDateFmt.setLenient(true);
    }

    /**
     * Direct constructor.  The actual expiration time is represented by a
     * string that can take one of three forms:
     *   1) a decimal integer preceded by a "+" character, representing a
     *       millisecond Unix time value,
     *   2) the string "never", representing an "expiration" that never
     *       expires,
     *   3) a well-formed Java date literal that will be parsed by the Java
     *       DateFormat class.  Note that this latter form is provided as a
     *       convenience for testing, but probably shouldn't be used in
     *       production as the Java date parser is very finicky.
     *
     * @param dateString   String representation of the expiration date and
     *    time.
     */
    ExpirationDate(String dateString) throws ParseException {
        if (dateString.startsWith("+")) {
            try {
                myTime = Long.parseLong(dateString.substring(1));
            } catch (NumberFormatException e) {
                throw new ParseException("bad date format: " + e, 0);
            }
        } else if (dateString.equals("never")) {
            myTime = Long.MAX_VALUE;
        } else {
            Date parsedDate = theDateFmt.parse(dateString);
            myTime = parsedDate.getTime();
        }
    }

    /**
     * JSON-driven constructor.
     *
     * @param time  Millisecond clock time when expiration happens
     */
    @JSONMethod({ "when" })
    ExpirationDate(long time) {
        if (time == 0) {
            myTime = Long.MAX_VALUE;
        } else {
            myTime = time;
        }
    }

    /**
     * Encode this expiration date for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this expiration date.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral(control);
        long encTime = myTime;
        if (myTime == Long.MAX_VALUE) {
            encTime = 0;
        }
        result.addParameter("when", encTime);
        result.finish();
        return result;
    }

    /**
     * Compare this expiration date to another according to the dictates of the
     * standard Java Comparable interface.
     *
     * @param other  The other date to compare to.
     *
     * @return a value less than, equal to, or greater than zero according to
     *    whether this expiration date is before, at, or after 'other'.
     */
    public int compareTo(Object other) {
        ExpirationDate otherDate = (ExpirationDate) other;
        long deltaTime = myTime - otherDate.myTime;
        if (deltaTime < 0) {
            return -1;
        } else if (deltaTime > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Test if this expiration date has already passed.
     *
     * @return true if this object represents an expired date, false if not.
     */
    boolean isExpired() {
        return myTime < System.currentTimeMillis();
    }

    /**
     * Produce a legible representation of this expiration date.
     *
     * @return this expiration date as a string.
     */
    public String toString() {
        if (myTime == Long.MAX_VALUE) {
            return "never";
        } else {
            return "+" + myTime;
        }
    }
}
