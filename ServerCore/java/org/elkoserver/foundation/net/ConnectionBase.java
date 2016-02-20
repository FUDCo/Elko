package org.elkoserver.foundation.net;

import org.elkoserver.foundation.run.Runner;
import org.elkoserver.util.trace.Trace;

/**
 * Base class providing common internals implementation for various types of
 * Connection objects.
 */
public abstract class ConnectionBase implements Connection {
    /** Token to put on send queue to signal close of connection. */
    protected static final Object theCloseMarker = new Object();

    /** Counter for allocating connection IDs. */
    private static int theIDCounter = 0;

    /** Number identifying this connection in log messages. */
    private int myID;
    
    /** Handler for incoming messages. */
    private MessageHandler myMessageHandler;

    /** The run queue in which messages will be handled. */
    /* protected */ Runner myRunner;

    /** System load tracker. */
    private LoadMonitor myLoadMonitor;

    /**
     * Constructor.
     *
     * @param mgr  Network manager for this server.
     */
    protected ConnectionBase(NetworkManager mgr) {
        myMessageHandler = null;
        myRunner = mgr.runner();;
        myLoadMonitor = mgr.loadMonitor();
        myID = theIDCounter++;
    }

    /**
     * Cope with loss of a connection.
     *
     * @param reason  Throwable describing why the connection died.
     */
    protected void connectionDied(Throwable reason) {
        if (myMessageHandler != null) {
            if (Trace.comm.debug && Trace.ON) {
                Trace.comm.debugm(this + " calls connectionDied in " +
                                  myMessageHandler);
            }
            myMessageHandler.connectionDied(this, reason);
        } else {
            Trace.comm.debugm(this +
                " ignores connection death while message handler is null");
        }
    }

    /**
     * Identify this connection for logging purposes.
     *
     * @return this connection's ID number.
     */
    public int id() {
        return myID;
    }

    /**
     * Enqueue a received message for processing.
     *
     * @param message  The received message.
     */
    protected void enqueueReceivedMessage(Object message) {
        myRunner.enqueue(new MessageHandlerThunk(message));
    }

    /**
     * Process a received message from the run queue.
     */
    private class MessageHandlerThunk implements Runnable {
        private Object myMessage;
        private long myOnQueueTime;
        
        MessageHandlerThunk(Object message) {
            myMessage = message;
            if (myLoadMonitor != null) {
                myOnQueueTime = System.currentTimeMillis();
            }
        }
        
        public void run() {
            if (myMessageHandler != null) {
                if (Trace.comm.verbose && Trace.ON) {
                    Trace.comm.verbosem(ConnectionBase.this +
                        " calls processMessage in " + myMessageHandler);
                }
                myMessageHandler.processMessage(ConnectionBase.this,
                                                myMessage);
            } else {
                Trace.comm.verbosem(ConnectionBase.this +
                    " ignores message received while message handler is null");
            }
            if (myLoadMonitor != null) {
                myLoadMonitor.addTime(
                    System.currentTimeMillis() - myOnQueueTime);
            }
        }
    }

    /**
     * Enqueue a task to invoke a message handler factory to produce a message
     * handler for this connection.
     *
     * @param handlerFactory  Provider of a message handler to process messages
     *    received on this connection.
     */
    protected void enqueueHandlerFactory(MessageHandlerFactory handlerFactory)
    {
        myRunner.enqueue(new HandlerFactoryThunk(handlerFactory));
    }

    private class HandlerFactoryThunk implements Runnable {
        private MessageHandlerFactory myHandlerFactory;
        HandlerFactoryThunk(MessageHandlerFactory handlerFactory) {
            myHandlerFactory = handlerFactory;
        }
        public void run() {
            myMessageHandler =
                myHandlerFactory.provideMessageHandler(ConnectionBase.this);
            if (myMessageHandler == null) {
                if (Trace.comm.debug && Trace.ON) {
                    Trace.comm.debugm(this + " connection setup failed");
                }
                close();
            }
        }
    }

    /**
     * Turn on or off debug features on this connection.
     *
     * This is an empty implementation so that subclasses only need to
     * implement this feature if they wish to.
     *
     * @param mode  If true, turn debug mode on; if false, turn it off.
     */
    public void setDebugMode(boolean mode) {
        /* Children may implement this method at their option. */
    }
}

