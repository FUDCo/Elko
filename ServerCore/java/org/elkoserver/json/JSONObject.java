package org.elkoserver.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A parsed JSON object.
 *
 * This class represents a JSON object that has been received or is being
 * constructed.  It provides random access to the properties of the object.
 */
public class JSONObject {
    /** Table where the object's properties are kept.  Maps from property names
        to property values. */
    private Map<String, Object> myProperties;

    /**
     * Construct a new, empty JSON object.
     */
    public JSONObject() {
        myProperties = new HashMap<String, Object>();
    }

    /**
     * Construct a JSON object from a pre-existing map.
     *
     * @param map A map mapping JSON property names to their values.  All the
     *   keys in this map must be strings and the values must be {@link
     *   Boolean}, {@link Double}, {@link Long}, {@link String}, {@link
     *   JSONArray}, or {@link JSONObject}.
     */
    public JSONObject(Map<String, Object> map) {
        myProperties = new HashMap<String, Object>(map);
    }

    /**
     * Construct a JSON object by copying another pre-existing JSON object.
     *
     * @param original  The original JSON object to be copied.
     */
    public JSONObject(JSONObject original) {
        myProperties = new HashMap<String, Object>(original.myProperties);
    }

    /**
     * Construct a JSON object representing a typed struct.
     *
     * @param type  The type name (this will be added as the property
     *    'type:').
     */
    public JSONObject(String type) {
        this();
        addProperty("type", type);
    }

    /**
     * Construct a JSON object representing a message.
     *
     * @param target  The reference ID of the message target (this will be
     *    added as the property 'to:').
     * @param verb  The message verb (this will be added as the property
     *    'op:').
     */
    public JSONObject(String target, String verb) {
        this();
        addProperty("to", target);
        addProperty("op", verb);
    }

    /**
     * Add a property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its value.
     *
     * Although class declaration rules of Java compell 'value' to be declared
     * as class {@link Object}, in reality it must be null or one of the
     * classes: {@link Boolean}, {@link Double}, {@link Long}, {@link String},
     * {@link JSONArray}, {@link JSONObject}.
     */
    public void addProperty(String name, Object value) {
        myProperties.put(name, value);
    }

    /**
     * Add a boolean property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its (boolean) value.
     */
    public void addProperty(String name, boolean value) {
        myProperties.put(name, new Boolean(value));
    }

    /**
     * Add a double property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its (double) value.
     */
    public void addProperty(String name, double value) {
        myProperties.put(name, new Double(value));
    }

    /**
     * Add an integer property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its (integer) value.
     */
    public void addProperty(String name, int value) {
        myProperties.put(name, new Long(value));
    }

    /**
     * Add a long property to the object.
     *
     * @param name  The name of the property to add.
     * @param value  Its (integer) value.
     */
    public void addProperty(String name, long value) {
        myProperties.put(name, new Long(value));
    }

    /**
     * Add an object that can be encoded as JSON to an object.  The value given
     * is encoded, than parsed into a JSON object.
     *
     * @param name  The name of the property to add.
     * @param value  Its (Encodable) value.
     */
    public void addProperty(String name, Encodable value) {
        try {
            myProperties.put(
                name,
                parse(value.encode(EncodeControl.forRepository).sendableString()));
        } catch (SyntaxError e) {
            /* This can't happen */
        }
    }

    /**
     * Add a property to the object by copying a property of another object.
     * If the object being copied from does not possess the indicated property,
     * this operation has no effect.
     *
     * @param name  The name of the property to copy.
     * @param orig  The original object to copy from.
     */
    public void copyProperty(String name, JSONObject orig) {
        Object value = orig.getProperty(name);
        if (value != null) {
            myProperties.put(name, value);
        }
    }

    /**
     * Obtain an array property value.
     *
     * @param name  The name of the array property sought.
     *
     * @return  The JSON array value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not an array.
     */
    public JSONArray getArray(String name) throws JSONDecodingException {
        try {
            JSONArray obj = (JSONArray) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an array as was expected");
        }
    }

    /**
     * Obtain the boolean value of a property.
     *
     * @param name  The name of the boolean property sought.
     *
     * @return  The boolean value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not boolean.
     */
    public boolean getBoolean(String name) throws JSONDecodingException {
        try {
            Boolean obj = (Boolean) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj.booleanValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a boolean value as was expected");
        }
    }

    /**
     * Obtain a double property value.
     *
     * @param name  The name of the double property sought.
     *
     * @return  The double value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a number.
     */
    public double getDouble(String name) throws JSONDecodingException {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj.doubleValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a floating-point numeric value as was expected");
        }
    }

    /**
     * Obtain an integer property value.
     *
     * @param name  The name of the integer property sought.
     *
     * @return  The int value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a number.
     */
    public int getInt(String name) throws JSONDecodingException{
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj.intValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain a long property value.
     *
     * @param name  The name of the long property sought.
     *
     * @return  The long value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a number.
     */
    public long getLong(String name) throws JSONDecodingException {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj.longValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the value of a property.
     *
     * @param name  The name of the property sought
     *
     * @return  An object representing the value of the property named by
     *    'name', or null if the object has no such property.
     */
    public Object getProperty(String name) {
        return myProperties.get(name);
    }

    /**
     * Obtain a JSON object property value.
     *
     * @param name  The name of the object property sought.
     *
     * @return  The JSON object value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' or
     *    if the value is not a JSON object.
     */
    public JSONObject getObject(String name) throws JSONDecodingException {
        try {
            JSONObject obj = (JSONObject) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain a string property value.
     *
     * @param name  The name of the string property sought.
     *
     * @return  The string value of the property named by 'name'.
     *
     * @throws JSONDecodingException if no value is associated with 'name' if
     *    the value is not a string.
     */
    public String getString(String name) throws JSONDecodingException {
        try {
            String obj = (String) myProperties.get(name);
            if (obj == null) {
                throw new JSONDecodingException("property '" + name +
                                                "' not found");
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a string value as was expected");
        }
    }

    /**
     * Convert this JSONObject into a JSONLiteral.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public JSONLiteral literal(EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(control);

        /* What follows is a little bit of hackery to ensure that the canonical
           message and object properties ("to:", "op:", and "type:") are
           output first, regardless of what order the iterator spits all the
           properties out in.  This is strictly for the sake of legibility and
           has no deeper semantics. */

        literal.addParameterOpt("to", myProperties.get("to"));
        literal.addParameterOpt("op", myProperties.get("op"));
        literal.addParameterOpt("type", myProperties.get("type"));

        for (Map.Entry entry : myProperties.entrySet()) {
            Object key = entry.getKey();
            if (!key.equals("to") && !key.equals("op") && !key.equals("type")){
                literal.addParameter((String) key, entry.getValue());
            }
        }
        literal.finish();
        return literal;
    }

    /**
     * Encode this JSONObject into an externally provided string buffer.
     *
     * @param buf  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    /* package */ void encodeLiteral(StringBuffer buf, EncodeControl control) {
        JSONLiteral literal = new JSONLiteral(buf, control);

        for (Map.Entry entry : myProperties.entrySet()) {
            literal.addParameter((String) entry.getKey(), entry.getValue());
        }
        literal.finish();
    }

    /**
     * Obtain an array property value or an empty array if the property has no
     * value.
     *
     * @param name  The name of the array property sought.
     *
     * @return  The JSON array value of the property named by 'name' or an
     *    empty array if the named property has no value.
     *
     * @throws JSONDecodingException if property has a value but the value is
     *    not an array.
     */
    public JSONArray optArray(String name) throws JSONDecodingException {
        return optArray(name, new JSONArray());
    }

    /**
     * Obtain an array property value or a default value if the property has no
     * value.
     *
     * @param name  The name of the array property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The JSON array value of the property named by 'name' or
     *    'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if property has a value but the value is
     *    not an array.
     */
    public JSONArray optArray(String name, JSONArray defaultValue)
        throws JSONDecodingException
    {
        try {
            JSONArray obj = (JSONArray) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an array value as was expected");
        }
    }

    /**
     * Obtain the boolean value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the boolean property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The boolean value of the property named by 'name' or
     *    'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not boolean.
     */
    public boolean optBoolean(String name, boolean defaultValue)
        throws JSONDecodingException
    {
        try {
            Boolean obj = (Boolean) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.booleanValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a boolean value as was expected");
        }
    }

    /**
     * Obtain the double value of a property or a default value if the property
     * has no value.
     *
     * @param name  The name of the double property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The double value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public double optDouble(String name, double defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.doubleValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a floating-point numeric value as was expected");
        }
    }

    /**
     * Obtain the integer value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the integer property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The int value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public int optInt(String name, int defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.intValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the long value of a property or a default value if the property
     * has no value.
     *
     * @param name  The name of the long property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The long value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a number.
     */
    public long optLong(String name, long defaultValue)
        throws JSONDecodingException
    {
        try {
            Number obj = (Number) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj.longValue();
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not an integer numeric value as was expected");
        }
    }

    /**
     * Obtain the JSON object value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the JSON object property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The JSON object value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a {@link JSONObject}.
     */
    public JSONObject optObject(String name, JSONObject defaultValue)
        throws JSONDecodingException
    {
        try {
            JSONObject obj = (JSONObject) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain the JSON object value of a property or an empty object if the
     * property has no value.
     *
     * @param name  The name of the JSON object property sought.
     *
     * @return  The JSON object value of the property named by 'name' or a new
     *    empty JSON object if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a {@link JSONObject}.
     */
    public JSONObject optObject(String name) throws JSONDecodingException {
        try {
            JSONObject obj = (JSONObject) myProperties.get(name);
            if (obj == null) {
                return new JSONObject();
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a JSON object value as was expected");
        }
    }

    /**
     * Obtain the string value of a property or a default value if the
     * property has no value.
     *
     * @param name  The name of the string property sought.
     * @param defaultValue  The value to return if there is no such property.
     *
     * @return  The String value of the property named by 'name' or
     *   'defaultValue' if the named property has no value.
     *
     * @throws JSONDecodingException if the property has a value but the value
     *    is not a {@link String}.
     */
    public String optString(String name, String defaultValue)
        throws JSONDecodingException
    {
        try {
            String obj = (String) myProperties.get(name);
            if (obj == null) {
                return defaultValue;
            } else {
                return obj;
            }
        } catch (ClassCastException e) {
            throw new JSONDecodingException("property '" + name +
                "' is not a string value as was expected");
        }
    }

    /**
     * Create a JSON object by parsing a JSON object literal string.  If this
     * string contains more than one JSON literal, only the first one will be
     * parsed; if you have a string containing more than one JSON literal, use
     * {@link Parser} instead.
     *
     * @param str  A JSON literal string that will be parsed and turned into
     *    the corresponding JSONObject.
     */
    public static JSONObject parse(String str) throws SyntaxError {
        JSONObject result = new Parser(str).parseObjectLiteral();
        if (result == null) {
            throw new SyntaxError("empty JSON string");
        }
        return result;
    }

    /**
     * Get a set view of the properties of this JSON object.
     *
     * @return a set of this object's properties.
     */
    public Set<Map.Entry<String, Object>> properties() {
        return myProperties.entrySet();
    }

    /**
     * Remove a property from this JSON object.
     *
     * @param name  The name of the property to remove.
     *
     * @return the value of the property that was removed or null if there was
     *    no such property to remove.
     */
    public Object remove(String name) {
        return myProperties.remove(name);
    }

    /**
     * Obtain a {@link String} representation of this object suitable for
     * output to a connection.
     *
     * @return a sendable {@link String} representation of this object
     */
    public String sendableString() {
        return literal(EncodeControl.forClient).sendableString();
    }

    /**
     * Return the number of properties in this JSON object.
     *
     * @return the number properties in this JSON object.
     */
    public int size() {
        return myProperties.size();
    }

    /**
     * Interpreting this JSON object as a JSON message, obtain its target.
     *
     * @return the string value of the 'to' property if it has one, or null.
     */
    public String target() {
        return weakStringProperty("to");
    }

    /**
     * Obtain a printable string representation of this object.
     *
     * @return a printable representation of this object.
     */
    public String toString() {
        return literal(EncodeControl.forRepository).sendableString();
    }

    /**
     * Interpreting this JSON object as an encoded object descriptor, obtain
     * its type name.
     *
     * @return the string value of the 'type' property if it has one, or null.
     */
    public String type() {
        return weakStringProperty("type");
    }

    /**
     * Interpreting this JSON object as a JSON message, obtain its verb.
     *
     * @return the string value of the 'op' property if it has one, or null.
     */
    public String verb() {
        return weakStringProperty("op");
    }

    /**
     * Obtain the string value of a property or null if the property has no
     * value or is not a string.
     *
     * @param name  The name of the property sought.
     *
     * @return  The String value of the property named by 'name', if it exists
     *   and is a string, else null.
     */
    private String weakStringProperty(String name) {
        Object weakProperty = myProperties.get(name);
        if (weakProperty instanceof String) {
            return (String) weakProperty;
        } else {
            return null;
        }
    }
}
