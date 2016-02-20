package org.elkoserver.json;

/**
 * JSON parse token.  A token has a type (what kind of token it is) and
 * possibly a value.
 */
class Token {
    /* Token types */
    static final int BOOLEAN       =  1; // Boolean literal 'true' or 'false'
    static final int CLOSE_BRACE   =  2; // The '}' character
    static final int OPEN_BRACE    =  3; // The '{' character
    static final int CLOSE_BRACKET =  4; // The ']' character
    static final int OPEN_BRACKET  =  5; // The '[' character
    static final int COLON         =  6; // The ':' character
    static final int COMMA         =  7; // The ',' character
    static final int EOF           =  8; // End of input marker
    static final int FLOAT         =  9; // Floating point number literal
    static final int INTEGER       = 10; // Integer number literal
    static final int NULL          = 11; // Null object reference 'null'
    static final int STRING        = 12; // String literal
    static final int SYMBOL        = 13; // Alphanumeric symbol
    static final int SYNTAX_ERROR  = 14; // Syntax error marker
    static final int UNKNOWN       = 15; // Undecipherable token

    /* Predefined, preallocated tokens for fixed-value terminal symbols */
    static final Token theCloseBrace   = new Token(null, CLOSE_BRACE);
    static final Token theOpenBrace    = new Token(null, OPEN_BRACE);
    static final Token theCloseBracket = new Token(null, CLOSE_BRACKET);
    static final Token theOpenBracket  = new Token(null, OPEN_BRACKET);
    static final Token theColon        = new Token(null, COLON);
    static final Token theComma        = new Token(null, COMMA);
    static final Token theEOF          = new Token(null, EOF);
    static final Token theNull         = new Token(null, NULL);
    static final Token theSyntaxError  = new Token(null, SYNTAX_ERROR);
    static final Token theBooleanFalse = new Token(Boolean.FALSE, BOOLEAN);
    static final Token theBooleanTrue  = new Token(Boolean.TRUE, BOOLEAN);

    /**
     * The value of this token.  Some token types, such as STRING, denote a
     * class of tokens that are distinguished by value:
     *
     *     Type     Value type
     *     ----     ----------
     *     BOOLEAN  Boolean
     *     FLOAT    Float
     *     INTEGER  Integer
     *     STRING   String
     *     SYMBOL   String
     *
     * Terminals (e.g., punctuation characters) don't carry a value.
     */
    private Object myValue;

    /**
     * The type of this token.  This will be an integer in the range 1-15
     * encoding the type as with the fifteen token type values given above.
     */
    private int myType;

    /**
     * Constructor.
     *
     * @param value  Value for new token.
     * @param type  Type for new token.
     */
    Token(Object value, int type) {
        myValue = value;
        myType = type;
    }

    /**
     * Get this token's type.
     *
     * @return this Token's token type
     */
    int type() {
        return myType;
    }

    /**
     * Get this token's value string, if it has one.
     *
     * @return this Token's value as a symbol, assuming that it is one (and
     *    return null if it is not a symbol)
     */
    String symbol() {
        if (myType == SYMBOL || myType == STRING) {
            return (String) myValue;
        } else {
            return null;
        }
    }

    /**
     * Get this token's value.
     *
     * @return this Token's value
     */
    Object value() {
        return myValue;
    }
}

