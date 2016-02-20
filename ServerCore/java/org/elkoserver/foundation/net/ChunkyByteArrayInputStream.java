package org.elkoserver.foundation.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import org.elkoserver.util.trace.Trace;

/**
 * Input stream similar to ByteArrayInputStream but backed by an ongoing series
 * of byte arrays that can be added to during the stream object's lifetime.
 */
public class ChunkyByteArrayInputStream extends InputStream {
    /*
     * This object embodies a couple of assumptions about the nature of what is
     * being read and how this stream is being used:
     *
     *     (1) The input bytes may be UTF-8 minimally encoded characters.
     *     (2) The content is line-oriented text, like JSON messages or HTTP
     *         headers.
     *
     * A regular Java InputStreamReader can't be used because it buffers too
     * aggressively.  Its reads would outrun knowledge of the encoding of what
     * is being read, making it impossible to mix character and byte input from
     * the same source.  It is necessary to be able to mix these because some
     * legitimate input streams are themselves mixed: HTTP headers are encoded
     * in ASCII (according to RFC 2616), whereas the HTTP bodies of interest
     * will often (though not necessarily) be UTF-8.
     *
     * This class also must do its own UTF-8 decoding, because it reads from
     * multiple byte arrays.  There is no guarantee that a UTF-8 character
     * won't be split across two arrays.  If such a split were to happen,
     * Java's UTF-8 byte stream decoder would get a decoding exception on the
     * fractional character.
     *
     * This class exploits the two assumptions given: The UTF-8 minimal
     * encoding rules ensure that a newline will be a single-byte ASCII newline
     * character and that this byte (0x0A) will never appear inside a
     * multi-byte UTF-8 character.  Therefore, a buffer may be scanned for a
     * newline without actually decoding it.  When a newline is detected, it is
     * safe to UTF-8 decode up through that newline, since the newline itself
     * is a complete character.  Conversely, if there is no newline at the end
     * of the buffer, then it is safe to wait for more bytes to be received:
     * additional bytes are to be expected because the content is, by
     * definition, line-oriented.
     *
     * This class works thusly: input bytes are handed to this object in
     * byte-array buffers via the addBuffer() method.  These buffers are saved
     * internally to serve future read() calls.  However, only bytes up through
     * the last received newline character are actually available for reading.
     * If read() is called when there are no newlines in the buffers, the
     * read() call returns an end-of-file indication (i.e., read length -1).
     * If more bytes are provided by a later call to addBuffer() AND these new
     * bytes contain one or more newline characters, then additional bytes (up
     * through the last newline) become available for reading.  A true
     * end-of-file in the input is handed to this object by passing a
     * zero-length buffer to addBuffer().  If there are then any bytes
     * following the last newline in the buffers, an IOException will be thrown
     * after that last newline is read.
     *
     * This behavior means that if readASCIILine() or readUTFLine() returns an
     * EOF, it just means that there isn't a complete line in the buffers, not
     * that the actual end of input has been encountered.  An actual (normal)
     * EOF condition is signalled to readers by throwing an EOFException.  This
     * is an admitted abuse of the interface spec for InputStream motivated by
     * pragmatic concerns; the consumer of the input from this stream MUST be
     * coded in awareness of what is being done here.
     */

    /** Byte buffer currently being read from. */
    private byte[] myWorkingBuffer;

    /** Position of the next byte to read from the working buffer. */
    private int myWorkingBufferIdx;

    /** Number of bytes in working buffer that may be read. */
    private int myWorkingBufferLength;

    /** Additional byte arrays queued to be read. */
    private LinkedList<byte[]> myPendingBuffers;
    
    /** A byte array that has been passed to this object by its client but not
        yet copied to storage internal to this object. */
    private byte[] myClientBuffer;

    /** Number of bytes in client buffer that may be used. */
    private int myClientBufferLength;
    
    /** Number of bytes fed in that haven't yet been read. */
    private int myTotalByteCount;

    /** Number of unread bytes that can actually be returned right now. */
    private int myUsefulByteCount;
    
    /** Flag indicating an actual EOF in the input. */
    private boolean amAtEOF;

    /** Buffer for accumulating a line of decoded characters. */
    private StringBuffer myLine;

    /** Flag indicating that WebSocket framing is enabled. */
    private boolean amWebSocketFraming;
    
    /**
     * Constructor.  Initially, no input has been provided.
     */
    public ChunkyByteArrayInputStream() {
        myPendingBuffers = new LinkedList<byte[]>();
        myWorkingBuffer = null;
        myClientBuffer = null;
        myTotalByteCount = 0;
        myUsefulByteCount = 0;
        amAtEOF = false;
        amWebSocketFraming = false;
        myLine = new StringBuffer(1000);
    }
    
    /**
     * Be given a buffer full of input bytes.
     *
     * <p>Note: this class assumes that it may continue to freely make direct
     * use of the contents of the byte buffer that is given to this method
     * (i.e., without copying it to internal storage) until {@link
     * #preserveBuffers} is called; after that, the buffer contents may be
     * modifed externally.  This is somewhat delicate, but eliminates a vast
     * amount of unnecessary byte array allocation and copying.
     *
     * @param buf  The bytes themselves.
     * @param length  Number of bytes in 'buf' to read (<= buf.length).
     */
    public void addBuffer(byte[] buf, int length) {
        if (Trace.comm.debug && Trace.ON) {
            if (length == 0) {
                Trace.comm.debugm("receiving 0 bytes: || (EOF)");
            } else {
                Trace.comm.debugm("receiving " + length + " bytes: |" +
                                  Trace.byteArrayToASCII(buf, 0, length) +"|");
            }
        }
        if (length == 0) {
            amAtEOF = true;
        } else {
            preserveBuffers(); /* save previous client buffer */
            myClientBuffer = buf;
            myClientBufferLength = length;
            for (int i = 0; i < length; ++i) {
                if (buf[i] == '\n' || (amWebSocketFraming && buf[i] == -1)) {
                    myUsefulByteCount = myTotalByteCount + i + 1;
                }
            }
            myTotalByteCount += length;
        }
    }
    
    /**
     * Get the number of bytes that can be read from this input stream without
     * blocking.  Since this class never actually blocks, this is just the
     * number of bytes available at the moment.
     *
     * @return the number of bytes that can be read from this input stream.
     */
    public int available() throws IOException {
        return myTotalByteCount;
    }
    
    /**
     * Copy any unread portions of the client buffer passed to {@link
     * #addBuffer}.  This has the side effect of passing responsibility for the
     * client buffer back to the client.  This indirection minimizes
     * unnecessary byte array allocation and copying.
     */
    public void preserveBuffers() {
        if (myClientBuffer != null) {
            if (myWorkingBuffer == myClientBuffer) {
                byte[] saveBuffer =
                    new byte[myWorkingBufferLength - myWorkingBufferIdx];
                System.arraycopy(myWorkingBuffer, myWorkingBufferIdx,
                                 saveBuffer,      0,
                                 saveBuffer.length);
                myWorkingBuffer = saveBuffer;
                myWorkingBufferLength = saveBuffer.length;
                myWorkingBufferIdx = 0;
            } else {
                byte[] saveBuffer = new byte[myClientBufferLength];
                System.arraycopy(myClientBuffer, 0,
                                 saveBuffer,     0,
                                 saveBuffer.length);
                myPendingBuffers.add(saveBuffer);
            }
            myClientBuffer = null;
        }
    }
    
    /**
     * Read the next byte of data from the input stream.  The byte value is
     * returned as an int in the range 0 to 255.  If no byte is available,
     * the value -1 is returned.
     *
     * @return the next byte of data, or -1 if the end of the currently
     *    available input is reached.
     *
     * @throws IOException if an incomplete line is in the buffers upon the
     *    true end of input.
     * @throws EOFException if the true end of input is reached normally
     */
    public int read() throws IOException {
        if (testEnd()) {
            return -1;
        } else {
            return readByte();
        }
    }

    /**
     * Read the next actual byte from the input stream.  The byte value is
     * returned as an int in the range 0 to 255.  This method must only be
     * called if it is know that a byte is actually available!
     *
     * @return the next byte of data.
     */
    private int readByte() throws IOException {
        if (myWorkingBuffer == null) {
            if (myPendingBuffers.size() > 0) {
                myWorkingBuffer = myPendingBuffers.removeFirst();
                myWorkingBufferLength = myWorkingBuffer.length;
            } else if (myClientBuffer != null) {
                myWorkingBuffer = myClientBuffer;
                myWorkingBufferLength = myClientBufferLength;
            } else {
                return -1;
            }
            myWorkingBufferIdx = 0;
        }
        int result = myWorkingBuffer[myWorkingBufferIdx++];
        if (myWorkingBufferIdx >= myWorkingBufferLength) {
            if (myWorkingBuffer == myClientBuffer) {
                myClientBuffer = null;
            }
            myWorkingBuffer = null;
        }
        --myTotalByteCount;
        if (myUsefulByteCount > 0) {
            --myUsefulByteCount;
        }
        return result & 0xFF;
    }

    /**
     * Read a fixed number of bytes from the input stream.  The result is
     * returned as a byte array of the requested length.  If insufficient data
     * is available, null is returned.
     *
     * @param count  The number of bytes desired
     *
     * @return an array of 'count' bytes, or null if that many bytes are not
     *    currently available.
     *
     * @throws IOException if the true end of input is reached normally
     */
    public byte[] readBytes(int count) throws IOException {
        if (myTotalByteCount < count) {
            return null;
        } else {
            byte[] result = new byte[count];
            for (int i = 0; i < count; ++i) {
                result[i] = (byte) readByte();
            }
            return result;
        }
    }

    /**
     * Read the next UTF-8 encoded character from the input stream.  If
     * another full character is not available, -1 is returned, even if there
     * are still bytes remaining in the input stream.
     *
     * @return the next character in the input, or -1 if the end of the
     *    currently available input is reached.
     *
     * @throws IOException if an incomplete line is in the buffers upon
     *    encountering the true end of input.
     */
    public int readUTF8Char() throws IOException {
        int byteA = read();
        if (byteA == -1) {
            /* EOF */
            return -1;
        } else if (amWebSocketFraming && byteA == 0x00) {
            /* WebSocket start-of-frame: return a nul; it will be ignored */
            return 0;
        } else if (amWebSocketFraming && byteA == 0xFF) {
            /* WebSocket end-of-frame: pretend it's a newline */
            return '\n';
        } else if ((byteA & 0x80) == 0) {
            /* One byte UTF-8 character */
            return byteA;
        } else if ((byteA & 0xE0) == 0xC0) {
            /* Two byte UTF-8 character */
            int byteB = read();
            if ((byteB & 0xC0) == 0x80) {
                return ((byteA & 0x1F) << 6) |
                        (byteB & 0x3F);
            }
        } else if ((byteA & 0xF0) == 0xE0) {
            /* Three byte UTF-8 character */
            int byteB = read();
            if ((byteB & 0xC0) == 0x80) {
                int byteC = read();
                if ((byteC & 0xC0) == 0x80) {
                    return ((byteA & 0x0F) << 12) |
                           ((byteB & 0x3F) <<  6) |
                            (byteC & 0x3F);
                }
            }
        }
        throw new IOException("bad UTF-8 encoding");
    }

    /**
     * Read the next line of raw ASCII characters from the input stream.
     * However, if a complete line is not available in the buffers, null is
     * returned.
     *
     * <p>Takes ASCII characters from the buffers until a newline (optionally
     * preceded by a carriage return) is read, at which point the line is
     * returned as a String, not including the line terminator character(s).
     *
     * @return the next line of ASCII characters in the input, or null if
     *    another complete line is not currently available.
     *
     * @throws EOFException if the true end of input is reached.
     */
    public String readASCIILine() throws IOException {
        return readLine(false);
    }

    /**
     * Common read logic for readASCIILine() and readUTF8Line().
     *
     * @param doUTF8  If true, read UTF-8 characters; if false, read ASCII
     *    characters.
     *
     * @return the next line of characters in the input according the doUTF8
     *    flag, or null if another complete line is not currently available.
     *
     * @throws EOFException if the true end of input is reached.
     */
    private String readLine(boolean doUTF8) throws IOException {
        myLine.setLength(0);
        int inCharCode = doUTF8 ? readUTF8Char() : read();
        if (inCharCode == -1) {
            return null;
        } else {
            char inChar = (char) inCharCode;
            if (inChar == '\n') {
                return "";
            } else {
                do {
                    if (inChar != '\r' && inChar != '\n' && inChar != 0) {
                        myLine.append(inChar);
                    }
                    inChar = (char) (doUTF8 ? readUTF8Char() : read());
                } while (inChar != '\n');
                return myLine.toString();
            }
        }
    }

    /**
     * Read the next line of UTF-8 encoded characters from the input stream.
     * Howerver, If a complete line is not available in the buffers, null is
     * returned.
     *
     * <p>Takes UTF-8 characters from the buffers until a newline (optionally
     * preceded by a carriage return) is read, at which point the line is
     * returned as a String, not including the line terminator character(s).
     *
     * @return the next line of UTF-8 characters in the input, or null if
     *    another complete line is not currently available.
     *
     * @throws EOFException if the true end of input is reached.
     */
    public String readUTF8Line() throws IOException {
        return readLine(true);
    }

    /**
     * Read a string of UTF-8 encoded characters from the input stream.  Will
     * read until all currently available characters or 'byteCount' bytes are
     * consumed, whichever happens first.
     *
     * @param byteCount  Number of bytes of "good" UTF-8 data known to be
     *    available for reading.
     */
    public String readUTF8String(int byteCount) throws IOException {
        if (myUsefulByteCount < byteCount) {
            myUsefulByteCount = byteCount;
        }
        myLine.setLength(0);
        int inChar = readUTF8Char();
        if (inChar == -1) {
            return null;
        }
        while (inChar != -1) {
            myLine.append((char) inChar);
            inChar = readUTF8Char();
        }
        return myLine.toString();
    }

    /**
     * Control the enabling of WebSocket framing.  If WebSocket framing is on,
     * messages are assumed to be framed between 0x00 and 0xFF bytes, per the
     * WebSockets standard.  The 0x00 bytes of any messages will be ignored,
     * but the 0xFF bytes will be translated into newlines (and will be
     * regarded as line terminators for purposes of determining the
     * availability of received data in the input buffers).
     *
     * @param on  New setting for the WebSocket framing flag: true means
     *    WebSocket framing is enabled, false means it is disabled.
     */
    void setWebSocketFraming(boolean on) {
        amWebSocketFraming = on;
    }

    /**
     * Test for the end of available input.
     *
     * @return true if no more input is available at this time, false if input
     *    is available.
     *
     * @throws IOException if the true EOF is reached prior to an end of line.
     * @throws EOFException if the true EOF is reached properly.
     */
    private boolean testEnd() throws IOException {
        if (myUsefulByteCount == 0) {
            if (amAtEOF) {
                if (myTotalByteCount > 0) {
                    throw new IOException("undecodeable bytes left over");
                } else {
                    throw new EOFException();
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}
