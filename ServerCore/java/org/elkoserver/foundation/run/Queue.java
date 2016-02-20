package org.elkoserver.foundation.run;

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.NoSuchElementException;


/**
 * A conventional fifo queue in which dequeued items are removed in the same
 * order they were enqueued.  An untyped queue can hold any object (except
 * null).  A queue can be created with a dynamic type, in which case, at no
 * extra overhead, enqueue will only enqueue objects of that type (or a
 * subtype, but not null).  This check imposes no extra overhead, since Java
 * always makes us pay for a dynamic type check on array store anyway.
 *
 * Queue is a thread-safe data structure, providing its own lock, and a
 * blocking {@link #dequeue} operation.
 */
public class Queue implements Enumeration {
    static private final int INITIAL_SIZE = 400;

    private Object myQLock;
    private Object[] myStuff;
    private int myMaxSize;
    private int myCurSize;
    private int myOut;
    private int myIn;

    /**
     * Makes a Queue that can hold any object.
     */
    public Queue() {
        this(Object.class);
    }

    /**
     * Makes a Queue that can hold objects of the specified
     * <tt>elementType</tt>.
     *
     * @param elementType may not be a primitive (ie, scalar) type.
     */
    public Queue(Class elementType) {
        myQLock = new Object();
        if (elementType.isPrimitive()) {
            throw new IllegalArgumentException("must be reference type: " + 
                                               elementType);
        }                                       
        myStuff = (Object[])Array.newInstance(elementType, INITIAL_SIZE);
        myMaxSize = INITIAL_SIZE;
        myCurSize = 0;
        myOut = 0;
        myIn = 0;
    }

    /**
     * Get the least-recently-added element off of the queue.  If the queue 
     * is currently empty, block until there is an element that can be
     * dequeued. 
     */
    public Object dequeue() {
        synchronized (myQLock) {
            while(true) {
                Object result = optDequeue();
                if (result != null) {
                    return result;
                }
                try {
                    myQLock.wait();
                } catch (InterruptedException ie) {
                    /* Ignored on purpose, but we do recheck the queue rather 
                       than just waiting again. */
                }
            }
        }
    }

    /**
     * Add a new element to the queue.
     *
     * @param newElement the object to be added to the end of the queue.
     *
     * @throws NullPointerException thrown if newElement is null
     * @throws ArrayStoreException thrown if newElement does not conform 
     *    to the elementType specified in the Queue constructor.
     */
    public void enqueue(Object newElement) {
        if (newElement == null) {
            throw new NullPointerException("cannot enqueue a null");
        }
        synchronized (myQLock) {
            /* grow array if necessary */
            if (myCurSize == myMaxSize) {
                int newSize = (myMaxSize * 3) / 2 + 10;
                Class elementType = myStuff.getClass().getComponentType();
                Object[] stuff = (Object[])Array.newInstance(elementType,
                                                             newSize);

                /* note: careful code to avoid inadvertantly reordering msgs */
                System.arraycopy(myStuff, myOut, stuff, 0, myMaxSize - myOut);
                if (myOut != 0) {
                    System.arraycopy(myStuff, 0, stuff, myMaxSize - myOut, 
                                     myOut);
                }
                myOut = 0;
                myIn = myMaxSize;
                myStuff = stuff;
                myMaxSize = newSize;
            }
            /* Will throw ArrayStoreException if newElement's type doesn't 
               conform to elementType */
            myStuff[myIn] = newElement;
            ++myIn;
            if (myIn == myMaxSize) {
                myIn = 0;
            }
            ++myCurSize;
            myQLock.notifyAll();
        }
    }

    /**
     * Check to see if the queue has more elements.  This method
     * allows a Queue to be used as an Enumeration.
     *
     * @return is false if the queue is empty, otherwise true
     */
    public boolean hasMoreElements() {
        return myCurSize != 0;
    }

    /**
     * Get the least-recently-added element off of the queue.  If the queue 
     * is currently empty, throw NoSuchElementException.  This method
     * allows a Queue to be used as an Enumeration.
     */
    public Object nextElement() throws NoSuchElementException {
        Object result = optDequeue();
        if (result == null) {
            throw new NoSuchElementException("queue is currently empty");
        }
        return result;
    }

    /**
     * Get the least-recently-added element off of the queue, or null
     * if the queue is currently empty.
     */
    public Object optDequeue() {
        if (myCurSize == 0) {
            return null;
        }

        synchronized (myQLock) {
            Object result = myStuff[myOut];

            myStuff[myOut] = null;
            ++myOut;
            if (myOut == myMaxSize) {
                myOut = 0;
            }
            --myCurSize;

            return result;
        }
    }
}
