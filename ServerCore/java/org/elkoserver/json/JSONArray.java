package org.elkoserver.json;

import java.util.ArrayList;

/**
 * A parsed JSON array.
 *
 * This class represents a JSON array that has been received or is being
 * constructed.
 */
public class JSONArray extends ArrayList<Object> {
    /**
     * Construct a new, empty array.
     */
    public JSONArray() {
        super();
    }

    /**
     * Encode this JSONArray into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    /* package */ void encodeLiteral(StringBuffer buf, EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(buf, control);
        for (Object element : this) {
            literal.addElement(element);
        }
        literal.finish();
    }

    /**
     * Convert this JSONArray into a JSONLiteralArray.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public JSONLiteralArray literal(EncodeControl control) {
        JSONLiteralArray literal = new JSONLiteralArray(control);
        for (Object element : this) {
            literal.addElement(element);
        }
        literal.finish();
        return literal;
    }

    /**
     * Obtain an array-valued element value.
     *
     * @param index  The index of the array value sought.
     *
     * @return  The JSON array value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not an array.
     */
    public JSONArray getArray(int index) throws JSONDecodingException {
        try {
            return (JSONArray) get(index);
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not an array as was expected");
        }
    }

    /**
     * Obtain the boolean value of an element.
     *
     * @param index  The index of the boolean value sought.
     *
     * @return  The boolean value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a boolean.
     */
    public boolean getBoolean(int index) throws JSONDecodingException {
        try {
            return ((Boolean) get(index)).booleanValue();
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a boolean value as was expected");
        }
    }

    /**
     * Obtain the double value of an element.
     *
     * @param index  The index of the double value sought.
     *
     * @return  The double value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a number.
     */
    public double getDouble(int index) throws JSONDecodingException {
        try {
            return ((Number) get(index)).doubleValue();
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a floating-point numeric value as was expected");
        }
    }

    /**
     * Obtain the integer value of an element.
     *
     * @param index  The index of the integer value sought.
     *
     * @return  The int value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a number.
     */
    public int getInt(int index) throws JSONDecodingException{
        try {
            return ((Number) get(index)).intValue();
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the long value of an element.
     *
     * @param index  The index of the long value sought.
     *
     * @return  The long value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a number.
     */
    public long getLong(int index) throws JSONDecodingException {
        try {
            return ((Number) get(index)).longValue();
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the JSON object value of an element.
     *
     * @param index  The index of the object value sought.
     *
     * @return  The JSON object value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a JSON object.
     */
    public JSONObject getObject(int index) throws JSONDecodingException {
        try {
            return (JSONObject) get(index);
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain the string value of an element.
     *
     * @param index  The index of the string value sought.
     *
     * @return  The string value of element number 'index'.
     *
     * @throws JSONDecodingException if the value is not a string.
     */
    public String getString(int index) throws JSONDecodingException {
        try {
            return (String) get(index);
        } catch (ClassCastException e) {
            throw new JSONDecodingException("element #" + index +
                " is not a string value as was expected");
        }
    }

    /**
     * Obtain a string representation of this array suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this array.
     */
    public String sendableString() {
        return literal(EncodeControl.forClient).sendableString();
    }

    /**
     * Obtain a printable string representation of this JSON array.
     *
     * @return a printable representation of this array.
     */
    public String toString() {
        return literal(EncodeControl.forRepository).sendableString();
    }
}
