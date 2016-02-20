package org.elkoserver.util.trace;

/**
 * Interface to be implemented by the entity that is to be notified of all
 * exceptions reported to {@link ExceptionManager} or which are uncaught.
 *
 * @see ExceptionManager
 */
public interface ExceptionNoticer 
{
    /**
     * Notification of a reported exception.
     *
     * @param msg  The message that accompanied the exception report.
     * @param t  The actual exception itself.
     */
    public void noticeReportedException(String msg, Throwable t);

    /**
     * Notification of an uncaught exception.
     *
     * @param msg  Message describing the circumstances.
     * @param t  The actual exception itself.
     */
    public void noticeUncaughtException(String msg, Throwable t);
}
