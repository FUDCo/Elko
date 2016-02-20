package org.elkoserver.foundation.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/**
 * Invoker subclass for constructors.  Uses Java reflection to invoke a
 * JSON-driven constructor that decodes a JSON object literal describing an
 * instance of a particular Java class.
 */
class ConstructorInvoker extends Invoker {
    /** The constructor to call. */
    private Constructor myConstructor;

    /** Flag to include the raw JSON object being decoded as a constructor
        parameter. */
    private boolean amIncludingRawObject;

    /**
     * Constructor.
     *
     * @param constructor  The JSON-driven constructor itself.
     * @param includeRawObject  If true, pass the JSON object being decoded
     *    as the first parameter to the constructor.
     * @param paramTypes  The types of the various parameters.
     * @param paramNames  JSON names for the parameters.
     */
    ConstructorInvoker(Constructor constructor, boolean includeRawObject,
                       Class paramTypes[], String paramNames[])
    {
        super(constructor, paramTypes, paramNames, includeRawObject ? 1 : 0);
        myConstructor = constructor;
        amIncludingRawObject = includeRawObject;
    }

    /**
     * Invoke the constructor on a JSON object descriptor.
     *
     * @param obj  JSON object describing what is to be constructed.
     * @param resolver  Type resolver for parameters.
     *
     * @return the result of calling the constructor, or null if the
     *    constructor failed.
     */
    Object construct(JSONObject obj, TypeResolver resolver) {
        try {
            if (amIncludingRawObject) {
                return apply(null, obj, obj.properties(), resolver);
            } else {
                return apply(null, null, obj.properties(), resolver);
            }
        } catch (JSONInvocationException e) {
            Trace.comm.errorm("error calling JSON constructor: " +
                              e.getMessage());
            return null;
        } catch (MessageHandlerException e) {
            Throwable report = e.getCause();
            if (report == null) {
                report = e;
            }
            Trace.comm.errorReportException(report,
                                            "calling JSON constructor");
            return null;
        }
    }

    /**
     * Actually call the constructor.
     *
     * This method is called only from the superclass, Invoker.
     *
     * @param target  Invocation target (ignored in this case since there is
     *    none for constructors).
     * @param params  Constructor parameters.
     *
     * @return the value returned by the constructor, or null if it failed.
     */
    protected Object invokeMe(Object target, Object[] params)
        throws IllegalAccessException, InvocationTargetException,
            ParameterMismatchException
    {
        try {
            return myConstructor.newInstance(params);
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalArgumentException e) {
            throw new ParameterMismatchException(params,
                myConstructor.getParameterTypes());
        }
    }
}
