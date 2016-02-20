package org.elkoserver.util.trace;

/**
 * Class for exception handling of trace.
 */
class TraceExceptionNoticer implements ExceptionNoticer 
{
    TraceExceptionNoticer() {
        ExceptionManager.setExceptionNoticer(this);
    }

    public void noticeReportedException(String msg, Throwable t) {
        Trace.runq.errorm(msg, t);
    }

    public void noticeUncaughtException(String msg, Throwable t) {
        Trace.runq.fatalError(msg, t);
    }
}
