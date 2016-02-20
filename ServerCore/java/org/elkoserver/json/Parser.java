package org.elkoserver.json;

/**
 * Parser to translate JSON strings into JSON objects.
 */
public class Parser {
    private int myReadPtr;
    private String myString;

    /**
     * Create a parser to parse a string.
     *
     * @param inbuf  The JSON string to be parsed. The string may contain
     *    multiple JSON objects, one of which will be returned by each
     *    successive call to {@link #parseObjectLiteral parseObjectLiteral()}.
     */
    public Parser(String inbuf) {
        myString = inbuf;
        myReadPtr = -1;
    }

    /**
     * Parse the next unparsed JSON object in the input string.
     *
     * @return the next {@link JSONObject} in the string being scanned, or
     *    null if the end of the string has been reached.
     */
    public JSONObject parseObjectLiteral() throws SyntaxError {
        Token token = scanToken();
        if (token == Token.theOpenBrace) {
            return parseOpenObjectLiteral();
        } else if (token == Token.theEOF) {
            return null;
        } else {
            syntaxError("expected '{'");
            return null; /* strictly to make compiler happy */
        }
    }

    /**
     * Parse a JSON object, given that we've seen the open brace.
     *
     * @return the parsed object.
     */
    private JSONObject parseOpenObjectLiteral() throws SyntaxError {
        JSONObject obj = new JSONObject();
        int propertyCount = 0;
        Token token = scanToken();
        while (token != Token.theCloseBrace) {
            if (token.type() == Token.SYMBOL || token.type() == Token.STRING) {
                String name = token.symbol();
                obj.addProperty(name, parseProperty());
                ++propertyCount;
                token = scanToken();
                if (token == Token.theComma) {
                    token = scanToken();
                } else if (token != Token.theCloseBrace) {
                    syntaxError("expected '}'");
                }
            } else {
                syntaxError("expected symbol or string");
            }
        }
        return obj;
    }

    /**
     * Look for a particular token as the next token.
     *
     * @param type  The type of token we expect to see
     *
     * @return true if the specified type of token was not seen (i.e., there's
     *    an error)
     */
    private boolean expectToken(int type) {
        return scanToken().type() != type;
    }

    /**
     * Recognize the trailing exponent ([=+]?[0-9]+) as we scan a literal
     * floating point number.
     *
     * @param buf  Buffer into which we are putting the floating point
     *    number token being scanned; this contains the text of the literal up
     *    to the point where we started looking for the trailing exponent.
     *
     * @return the token recognized (either float or syntax error).
     */
    private Token finishFloatExponent(int start) {
        char c = myString.charAt(++myReadPtr);
        if (c == '+' || c == '-') {
            c = myString.charAt(++myReadPtr);
        }
        if (Character.isDigit(c)) {
            while (Character.isDigit(c)) {
                c = myString.charAt(++myReadPtr);
            }
            String number = myString.substring(start, myReadPtr);
            --myReadPtr;
            try {
                return new Token(new Double(number), Token.FLOAT);
            } catch (NumberFormatException e) {
                return Token.theSyntaxError;
            }
        } else {
            return Token.theSyntaxError;
        }
    }

    /**
     * Recognize the tail of a floating point number (everything after the
     * decimal point) as we scan.
     *
     * @param buf  Buffer into which we are putting the floating point number
     *    token being scanned; this contains the text of the literal up to the
     *    point where we started looking for the tail
     * @param needDigits  true==>there must be digits after the decimal point
     *    (because there weren't any before it); false==>no digits required.
     *
     * @return the token recognized (either float or syntax error).
     */
    private Token finishFloatTail(int start, boolean needDigits) {
        boolean haveDigits = false;
        char c = myString.charAt(++myReadPtr);
        while (Character.isDigit(c)) {
            c = myString.charAt(++myReadPtr);
            haveDigits = true;
        }
        if (needDigits && !haveDigits) {
            return Token.theSyntaxError;
        }
        if (c == 'e' || c == 'E') {
            return finishFloatExponent(start);
        } else {
            String number = myString.substring(start, myReadPtr);
            --myReadPtr;
            try {
                return new Token(new Double(number), Token.FLOAT);
            } catch (NumberFormatException e) {
                return Token.theSyntaxError;
            }
        }        
    }

    /**
     * Test a character to see if it is a hexadecimal number digit
     *
     * @param c  The character being tested
     *
     * @return true if 'c' is in the set [0-9a-fA-f]
     */
    private boolean isHexDigit(char c) {
        return Character.isDigit(c) ||
             ('a' <= c && c <= 'f') ||
             ('A' <= c && c <= 'F');
    }

    /**
     * Parse an array literal.
     *
     * @return the parsed array literal
     */
    private JSONArray parseArrayLiteral() throws SyntaxError {
        JSONArray array = new JSONArray();
        Token token = scanToken();
        while (token != Token.theCloseBracket) {
            array.add(parseValue(token));
            token = scanToken();
            if (token == Token.theComma) {
                token = scanToken();
            } else if (token != Token.theCloseBracket) {
                syntaxError("expected ']'");
            }
        }
        return array;
    }

    /**
     * Parse the value of a single object property.
     *
     * @return the parsed value object
     */
    private Object parseProperty() throws SyntaxError {
        if (scanToken() == Token.theColon) {
            return parseValue(scanToken());
        } else {
            syntaxError("expected ':'");
            return null; /* strictly to make compiler happy */
        }
    }

    /**
     * Parse a literal value.
     *
     * @param token  The first token of the value literal.
     *
     * @return the parsed object
     */
    private Object parseValue(Token token) throws SyntaxError {
        switch (token.type()) {
            case Token.STRING:
            case Token.FLOAT:
            case Token.INTEGER:
            case Token.BOOLEAN:
            case Token.NULL:
                return token.value();
            case Token.OPEN_BRACE:
                return parseOpenObjectLiteral();
            case Token.OPEN_BRACKET:
                return parseArrayLiteral();
            default:
                syntaxError("expected value token");
                return null; /* strictly to make compiler happy */
        }
    }

    /**
     * Scan an escape sequence inside a string literal, given that we know
     * we've started one by recognizing the '\' character.
     *
     * @param buf  Buffer into which the result should be written
     *
     * @return buf with the escaped character appended, or null if there was an
     * error.
     */
    private StringBuffer scanEscapeSequence(StringBuffer buf) {
        int unicode = 0;
        char c = myString.charAt(++myReadPtr);

        switch (c) {
            case '0': case '1': case '2': case '3':
            case '4': case '5': case '6': case '7':
                while ('0' <= c && c <= '7') {
                    unicode = unicode * 8 + 'c' - '0';
                    c = myString.charAt(++myReadPtr);
                }
                --myReadPtr;
                break;
            case 'u':
            case 'x':
                for (int i = (c == 'u' ? 4 : 2); i > 0; --i) {
                    c = myString.charAt(++myReadPtr);
                    unicode = unicode * 16 + Character.digit(c, 16);
                }
                break;
            case 'b': unicode = '\b'; break;
            case 'f': unicode = '\f'; break;
            case 'n': unicode = '\n'; break;
            case 'r': unicode = '\r'; break;
            case 't': unicode = '\t'; break;
            default:  unicode = c;    break;
        }
        buf.append((char) unicode);
        return buf;
    }

    /**
     * Scan the rest of a number token, given that we know we've started one.
     *
     * @param c  The first character of the number
     *
     * @return The recognized token (nominally integer or float, but can be
     *   syntax error if the number literal was malformed).
     *
     * This is kind of hairy because of the floating point syntax.  A number
     * may correspond to any of the following lexical patterns:
     *
     * 0[0-7]*                                 octal integer
     * 0[xX][0-9a-fA-F]+                       hexadecimal integer
     * [-+]?[0-9]+                             decimal integer
     * [-+]?[0-9]+.[0-9]*{[eE][-+]?[0-9]+}?    float with optional fraction
     * [-+]?.[0-9]+{[eE][-+]?[0-9]+}?          float with optional integer
     *
     * To make this slightly more tractable, we are loose on the rules in two
     * ways:
     *  (1) we don't complain about '8' and '9' digits in octal numbers 
     *  (2) we allow octal and hex numbers to be preceded by a sign
     */
    private Token scanNumber(char c) {
        int radix = 10;
        int sign = 1;
        long intValue = 0;
        int start = myReadPtr;
        
        if (c == '-') {
            sign = -1;
            c = myString.charAt(++myReadPtr);
        } else if (c == '+') {
            c = myString.charAt(++myReadPtr);
        }
        if (c == '0') {
            c = myString.charAt(++myReadPtr);
            if (c == 'x' || c == 'X') {
                c = myString.charAt(++myReadPtr);
                if (!isHexDigit(c)) {
                    return Token.theSyntaxError;
                }
                while (isHexDigit(c)) {
                    intValue = intValue * 16 + Character.digit(c, 16);
                    c = myString.charAt(++myReadPtr);
                }
                --myReadPtr;
                return new Token(new Long(intValue * sign), Token.INTEGER);
            } else {
                radix = 8;
            }
        } else if (c == '.') {
            return finishFloatTail(start, true);
        }
        intValue = 0;
        while (Character.isDigit(c)) {
            intValue = intValue * radix + Character.digit(c, 10);
            c = myString.charAt(++myReadPtr);
        }
        if (c == '.') {
            return finishFloatTail(start, false);
        } else if (c == 'e' || c == 'E') {
            return finishFloatExponent(start);
        } else {
            --myReadPtr;
            return new Token(new Long(intValue * sign), Token.INTEGER);
        }
    }

    /**
     * Scan the rest of a literal string token, given that we know we've
     * started one by recognizing the initial '"' or "'" character.
     *
     * @param quote  The initial quote character, for matching the end
     *
     * @return The recognized token (nominally a string, but can be
     *   a syntax error if the string literal was malformed).
     *
     * The scanned string itself will be the value() of the Token returned.
     */
    private Token scanString(char quote) {
        StringBuffer buf = null;
        
        int start = myReadPtr + 1;
        while (true) {
            char c = myString.charAt(++myReadPtr);
            if (c == quote) {
                if (buf == null) {
                    return new Token(myString.substring(start, myReadPtr),
                                     Token.STRING);
                } else {
                    buf.append(myString.substring(start, myReadPtr));
                    return new Token(buf.toString(), Token.STRING);
                }
            } else if (c == '\\') {
                if (buf == null) {
                    buf = new StringBuffer(100);
                }
                buf.append(myString.substring(start, myReadPtr));
                buf = scanEscapeSequence(buf);
                if (buf == null) {
                    return Token.theSyntaxError;
                }
                start = myReadPtr + 1;
            }
        }
    }    

    /**
     * Scan the rest of a symbol token, given that we know we've started one.
     *
     * @param c  The first character of the symbol
     *
     * @return A symbol token for the symbol that was scanned. If the symbol
     *   string is "true" or "false", then the symbol is instead recognized as
     *   a boolean literal value  and a boolean is returned. Similarly, if the
     *   symbol string is "null", the the symbol is recognized as a null
     *   literal value and the corresponding token is returned.
     */
    private Token scanSymbol(char c) {
        int start = myReadPtr;
        while (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
            c = myString.charAt(++myReadPtr);
        }
        String identifier = myString.substring(start, myReadPtr);
        --myReadPtr;

        if (identifier.equals("false")) {
            return Token.theBooleanFalse;
        } else if (identifier.equals("true")) {
            return Token.theBooleanTrue;
        } else if (identifier.equals("null")) {
            return Token.theNull;
        } else {
            return new Token(identifier, Token.SYMBOL);
        }
    }

    /**
     * Return the next lexical token in the input stream.
     */
    private Token scanToken() {
        try {
            char c = skipWhitespace();
            switch (c) {
                case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                case 'g': case 'h': case 'i': case 'j': case 'k': case 'l':
                case 'm': case 'n': case 'o': case 'p': case 'q': case 'r':
                case 's': case 't': case 'u': case 'v': case 'w': case 'x':
                case 'y': case 'z': case 'A': case 'B': case 'C': case 'D':
                case 'E': case 'F': case 'G': case 'H': case 'I': case 'J':
                case 'K': case 'L': case 'M': case 'N': case 'O': case 'P':
                case 'Q': case 'R': case 'S': case 'T': case 'U': case 'V':
                case 'W': case 'X': case 'Y': case 'Z': case '_': case '$':
                    return scanSymbol(c);
                case '0': case '1': case '2': case '3': case '4': case '5':
                case '6': case '7': case '8': case '9': case '-': case '+':
                case '.':
                    return scanNumber(c);
                case '"': case '\'':
                    return scanString(c);
                case '{':
                    return Token.theOpenBrace;
                case '}':
                    return Token.theCloseBrace;
                case '[':
                    return Token.theOpenBracket;
                case ']':
                    return Token.theCloseBracket;
                case ',':
                    return Token.theComma;
                case ':':
                    return Token.theColon;
                default:
                    return Token.theSyntaxError;
            }
        } catch (StringIndexOutOfBoundsException e) {
            return Token.theEOF;
        }
    }

    /**
     * Read the next non-whitespace, non-comment character in the input stream
     *
     * @return the next non-whitespace character.
     */
    private char skipWhitespace() {
        while (true) {
            char c = myString.charAt(++myReadPtr);
            if (c == '/') {
                c = myString.charAt(++myReadPtr);
                if (c == '*') {
                    boolean sawStar = false;
                    while (true) {
                        c = myString.charAt(++myReadPtr);
                        if (c == '/' && sawStar) {
                            break;
                        } else if (c == '*') {
                            sawStar = true;
                        } else {
                            sawStar = false;
                        }
                    }
                } else if (c == '/') {
                    boolean sawReturn = false;
                    while (true) {
                        c = myString.charAt(++myReadPtr);
                        if (c == '\n') {
                            break;
                        } else if (sawReturn) {
                            --myReadPtr;
                            break;
                        } else if (c == '\r') {
                            sawReturn = true;
                        } else {
                            sawReturn = false;
                        }
                    }
                } else {
                    --myReadPtr;
                    return '/';
                }
            } else if (!Character.isWhitespace(c)) {
                return c;
            }
        }
    }

    /**
     * Construct and throw a syntax error exception
     *
     * @param message  Helpful message describing what parser had expected
     */
    private void syntaxError(String message) throws SyntaxError {
        throw new SyntaxError(message + " near position " + myReadPtr +
                              " in: ///" + myString + "///");
    }
}
