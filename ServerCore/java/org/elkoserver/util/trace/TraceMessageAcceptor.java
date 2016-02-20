package org.elkoserver.util.trace;

/**
 * This interface represents objects that accept messages and do something
 * useful with them.  A <tt>TraceMessageAcceptor</tt> lives in two main states:
 * prior to its starting environment being set up, and after that environment
 * is set up.  A <tt>TraceMessageAcceptor</tt> <i>may</i> choose to accept
 * messages prior to the completion of setup, but it may not make them
 * available to a user.<p>
 *
 * An instance of this interface is given to the {@link TraceController} via
 * its {@link TraceController#setAcceptor setAcceptor()} method.  If a message
 * acceptor is not provided in this way, the {@link TraceController} class will
 * provide a standard default implementation, which simply writes messages to a
 * log file.  Typically that default implementation is the one you want to use.
 * This interface exists for circumstances which require trace messages to
 * receive special handling (for example, to make the message appear in an
 * application server's log file using a vendor-proprietary logging API).
 */
public interface TraceMessageAcceptor
{
    /** 
     * Accept a message and do whatever is appropriate to make it visible to a
     * user, either now or later.  Typically this will involve an action such
     * as writing the message to a log file or displaying it in a trace
     * window.<p>
     *
     * Note that this method will be called <i>after</i> the message passes a
     * priority threshold check.  <tt>TraceMessageAcceptor</tt>s don't know
     * about priorities.<p>
     *
     * The <tt>TraceMessageAcceptor</tt> is allowed to unilaterally discard the
     * message.  Generally, this is done only if it was turned off by a control
     * external to this interface.
     *
     * @param message  The trace message to be handled.
     */
     void accept(TraceMessage message);

    /**
     * Modify the acceptor configuration based on a property setting.
     *
     * @param name  Property name.
     * @param value   Property value.
     *
     * @return <tt>true</tt> if the property was recognized and handled,
     *    <tt>false</tt> if not.
     */
    boolean setConfiguration(String name, String value);

    /** 
     * After this call, the <tt>TraceMessageAcceptor</tt> must obey settings
     * from the environment.  Before this call, it must defer taking any
     * visible action, because it can't yet know what action is
     * appropriate.  Note that the message acceptor may (is encouraged to)
     * accept messages before setup is complete, because some of those trace
     * messages might be useful.<p>
     *
     * It is an error to call this method more than once.
     */
    void setupIsComplete();
}
