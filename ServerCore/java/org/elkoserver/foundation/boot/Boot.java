package org.elkoserver.foundation.boot;

import java.io.IOException;
import org.elkoserver.util.trace.ExceptionManager;
import org.elkoserver.util.trace.TraceController;

/**
 * This is the universal startup class for applications using Elko.  It
 * performs the necessary initializations that all applications require, then
 * launches the application itself by instantiating an instance of the
 * application boot class and calling its {@link Bootable#boot boot()}
 * method.<p>
 *
 * The name of the application boot class should be the second argument on the
 * Java command line, according to the following usage: <pre>
 *     java org.elkoserver.foundation.boot.Boot <em>bootclass args...</em>
 * </pre>
 *
 * The boot class must implement {@link Bootable}.<p>
 *
 * In addition to regular application command line parameters, property
 * settings may also be given on the command line.  They are interpreted
 * according to the format in {@link BootProperties#scanArgs}.  Arguments that
 * set property values are removed from the arguments array before it is
 * presented to the application.<p>
 */
public class Boot extends Thread {
    /** Flag to suppress multiple launchings */
    static private boolean theHaveStartedFlag = false;

    /** Command line args */
    private String myArgs[];

    /**
     * Create the thread from which everything else will unfold.
     *
     * @param theThreadGroup The ThreadGroup to run in
     * @param args Command line arguments per Java language spec
     */
    private Boot(ThreadGroup threadGroup, String args[]) {
        super(threadGroup, "Elko Server Boot");
        myArgs = args;
    }

    /**
     * Create a <tt>Boot</tt> object and start it running.
     *
     * @param args  Command line arguments per the Java language spec.
     */
    static public void main(String args[]) {
        try {
            /* Lock out multiple invocations */
            if (theHaveStartedFlag) {
                assert false: "Boot.main() called twice";
            }
            theHaveStartedFlag = true;

            /* Launch the thread in a new thread group */
            new Boot(new EMThreadGroup("Elko Thread Group"), args).start();
        } catch (Throwable t) {
            /* All purpose top-level exception interception */
            ExceptionManager.reportException(t);
        }
    }

    /**
     * Thread group for all server application threads to run in.  Will punt
     * all uncaught exceptions to the {@link ExceptionManager} class.
     */
    static private class EMThreadGroup extends ThreadGroup {
        /**
         * Standard thread group constructor.
         *
         * @param name The name for the new thread group.
         */
        public EMThreadGroup(String name) {
            super(name);
        }
        
        /**
         * Handle uncaught exceptions by giving them to the
         * {@link ExceptionManager}.
         *
         * @param thread  The thread in which the exception was thrown (and not
         *    caught).
         * @param ex  The exception itself.
         */
        public void uncaughtException(Thread thread, Throwable ex) {
            ExceptionManager.uncaughtException(thread, ex);
        }
    }

    /**
     * The run method mandated by the {@link Thread} class.  This method is
     * required to be public by Java's scoping rules, but you should not call
     * it yourself.
     *
     * @internal Java's rules don't allow you to declare run() to throw any
     * exceptions, so this just wraps a call to the private method
     * startApplication() in a try/catch block.  The guts of startup are in
     * another method for improved legibility.
     */
    public void run() {
        try {
            startApplication();
        } catch (Exception e) {
            ExceptionManager.reportException(e,
                                             "Failure in application startup");
        }
    }

    /**
     * This is the actual server boot thread initialization.<p>
     *
     * When Boot is the first class on the java command line, main() gets
     * called with the remaining command line arguments.  The first such
     * remaining command line argument (myArgs[0]) should be the name of the
     * start class.
     *
     * @throws ClassNotFoundException when myArgs[0] does not correspond to
     *   a class on the CLASSPATH.
     *
     * @throws IllegalAccessException when myArgs[0] names a non-permitted
     *   class, or a class whose zero-argument constructor is not permitted.
     *
     * @throws InstantiationException when myArgs[0] names an uninstantiable
     *   class, such as an interface or an abstract class.
     *
     * @throws IOException when a file from which one is supposed to read
     *   further properties does not exist or cannot be read.  However, if
     *   the command line switch "-nodefaults" is not provided, but any of
     *   the default properties files do not exist, they are silently skipped
     *   rather than throwing an exception.  But if they *do* exist and aren't
     *   readable, they throw an exception like any other properties file.
     */
    private void startApplication() throws ClassNotFoundException, IOException,
         IllegalAccessException, InstantiationException
    {
        /* Extract property assignments from command line args */
        BootProperties props = new BootProperties();
        myArgs = props.scanArgs(myArgs);

        /* Start up tracing early, in case anyone needs it during setup */
        TraceController.start(props);

        /* Make an instance of the start class */
        if (myArgs.length < 1) {
            throw new Error("Boot needs class name to boot");
        }
        Bootable starter;
        try {
            starter = (Bootable) Class.forName(myArgs[0]).newInstance();
        } catch (ClassCastException e) {
            throw new ClassCastException(myArgs[0] + " isn't a Bootable");
        }

        /* Strip start class name from args array */
        String newArgs[] = new String[myArgs.length-1];
        System.arraycopy(myArgs, 1, newArgs, 0, newArgs.length);

        starter.boot(newArgs, props);
    }
}
