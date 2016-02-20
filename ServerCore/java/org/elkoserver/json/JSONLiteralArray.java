package org.elkoserver.json;

/**
 * A literal JSON string, representing an array, undergoing incremental
 * construction.
 *
 * Users of this class should call the constructor to begin creation of the
 * literal, incrementally add to it using the various
 * {@link #addElement addElement()} methods, then finally complete it by
 * calling the {@link #finish} method.  After the literal is completed, it may
 * be used as another literal's parameter value.
 */
public class JSONLiteralArray {
    /** The string under construction. */
    private StringBuffer myStringBuffer;

    /** Start of this literal's portion of buffer. */
    private int myStartPos;

    /** End of this literal's portion of buffer. */
    private int myEndPos;

    /** State of construction. */
    private int myState;

    /** Number of elements successfully added. */
    private int mySize;

    /** Encode control to indicate how this literal is being encoded */
    private EncodeControl myControl;

    /* The state values */
    private final int INITIAL  = 0; /* Have not yet added first element */
    private final int STARTED  = 1; /* Have added first element */
    private final int COMPLETE = 2; /* All done */

    /**
     * Begin a new array literal that will be filled in incrementally, with an
     * externally provided buffer.
     *
     * @param stringBuffer  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    /* package */ JSONLiteralArray(StringBuffer stringBuffer,
                                   EncodeControl control)
    {
        myStringBuffer = stringBuffer;
        myStartPos = stringBuffer.length();
        myEndPos = myStartPos;
        myStringBuffer.append("[");
        myState = INITIAL;
        mySize = 0;
        myControl = control;
    }

    /**
     * Begin a new array literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public JSONLiteralArray(EncodeControl control) {
        this(new StringBuffer(500), control);
    }

    /**
     * Begin a new array literal that will be filled in incrementally.
     */
    public JSONLiteralArray() {
        this(EncodeControl.forClient);
    }

    /**
     * Add an element to the incomplete array literal. Note that any element
     * value that encodes to the Java value null will be ignored (i.e., not
     * added to the literal).
     *
     * @param value  The element value.
     *
     * @throws Error if you try to add an element to literal that is already
     *    complete.
     */
    public void addElement(Object value) {
        if (value instanceof Object[]) {
            beginElement();
            Object[] valueArray = (Object[]) value;
            JSONLiteralArray arr =
                new JSONLiteralArray(myStringBuffer, myControl);
            for (int i = 0; i < valueArray.length; ++i) {
                arr.addElement(valueArray[i]);
            }
            arr.finish();
        } else if (value != null) {
            int start = myStringBuffer.length();
            boolean starting = (myState == INITIAL);
            beginElement();
            if (JSONLiteral.appendValueString(myStringBuffer, value,
                                              myControl)) {
                myStringBuffer.setLength(start);
                if (starting) {
                    myState = INITIAL;
                }
            } else {
                mySize += 1;
            }
        }
    }

    /**
     * Add an object element to an incomplete array.
     *
     * @param value  The ({@link Encodable}) element value.
     */
    public void addElement(Encodable value) {
        addElement((Object) value);
    }

    /**
     * Add a floating point element to an incomplete array.
     *
     * @param value  The (double) element value.
     */
    public void addElement(double value) {
        addElement((Object) new Double(value));
    }

    /**
     * Add a boolean element to an incomplete array.
     *
     * @param value  The (boolean) element value.
     */ 
    public void addElement(boolean value) {
        addElement((Object) new Boolean(value));
    }
    
    /**
     * Add an integer element to an incomplete array.
     *
     * @param value  The (int) element value.
     */
    public void addElement(int value) {
        addElement((Object) new Integer(value));
    }
    
    /**
     * Add a long element to an incomplete array.
     *
     * @param value  The (long) element value.
     */
    public void addElement(long value) {
        addElement((Object) new Long(value));
    }

    /**
     * Add a JSON object element to an incomplete array.
     *
     * @param value  The ({@link JSONObject}) element value.
     */
    public void addElement(JSONObject value) {
        addElement((Object) value);
    }

    /**
     * Add a reference element to an incomplete array.
     *
     * @param value  The ({@link Referenceable}) element value.
     */
    public void addElement(Referenceable value) {
        addElement((Object) value.ref());
    }

    /**
     * Add a string element to an incomplete array.
     *
     * @param value  The ({@link String}) element value.
     */
    public void addElement(String value) {
        addElement((Object) value);
    }

    /**
     * Prepend any necessary punctuation upon starting a new element, and
     * update the state of construction accordingly.
     */
    private void beginElement() {
        if (myState != COMPLETE) {
            if (myState == INITIAL) {
                myState = STARTED;
            } else {
                myStringBuffer.append(", ");
            }
        } else {
            throw new Error("attempt to add element to completed array");
        }
    }

    /**
     * Finish construction of the literal.
     *
     * @throws Error if you try to finish a literal that is already complete.
     */
    public void finish() {
        if (myState != COMPLETE) {
            myStringBuffer.append(']');
            myState = COMPLETE;
            myEndPos = myStringBuffer.length();
        } else {
            throw new Error("attempt to finish already completed array");
        }
    }

    /**
     * Obtain a string representation of this literal suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this literal.
     *
     * @throws Error if the literal is not finished.
     */
    public String sendableString() {
        if (myState != COMPLETE) {
            finish();
        }
        return myStringBuffer.substring(myStartPos, myEndPos);
    }

    /**
     * Obtain the array's element count.
     *
     * @return the number of elements in this array (so far).
     */
    public int size() {
        return mySize;
    }

    /**
     * Get the internal string buffer, for collusion with JSONLiteral.
     */
    StringBuffer stringBuffer() {
        return myStringBuffer;
    }

    /**
     * Obtain a printable String representation of this literal, in whatever
     * its current state is.
     *
     * @return a printable representation of this literal.
     */
    public String toString() {
        int end = myEndPos;
        if (myState != COMPLETE) {
            end = myStringBuffer.length();
        }
        return myStringBuffer.substring(myStartPos, end);
    }
}
