package org.elkoserver.foundation.json;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elkoserver.json.JSONObject;

/**
 * A collection of precomputed Java reflection information that can dispatch
 * JSON messages to methods of the appropriate classes.
 */
public class MessageDispatcher {
    /** Mapping of message verbs to MethodInvoker objects.  Each entry is
        actually the head of a linked list of MethodInvoker objects, each of
        which handles the verb for a different class.  To dispatch an incoming
        JSON message, the verb is used as a key to obtain the head of the
        handler list for that verb, then this list is searched sequentially for
        a handler that matches a class acceptable to the object to which the
        message was actually addressed. */
    private Map<String, MethodInvoker> myInvokers;

    /** Classes for which there is stored dispatch information, to avoid
        repeating reflection operations. */
    private Set<Class> myClasses;

    /** Type resolver for the type tags of JSON encoded message parameter
        objects. */
    private TypeResolver myResolver;

    /**
     * Constructor.  Creates an empty dispatcher.
     *
     * @param resolver Type resolver for the type tags of JSON encoded message
     *    parameter objects.
     */
    public MessageDispatcher(TypeResolver resolver) {
        myInvokers = new HashMap<String, MethodInvoker>();
        myClasses = new HashSet<Class>();
        myResolver = resolver;
    }

    /**
     * Perform the Java reflection operations needed to do JSON message
     * dispatch on a given Java class.
     *
     * The class must be a JSON message handler class.  Such classes have JSON
     * message handler methods marked with the {@link JSONMethod} annotation.
     * JSON message handler methods must have public scope, a return type of
     * void, and a least one parameter, the first of which must have a type
     * assignable to a variable of type {@link Deliverer}.
     *
     * The name of the method is the name of the JSON message verb that the
     * method handles.  The value of the attached {@link JSONMethod} annotation
     * is an array of Strings, one for each method parameter except the initial
     * {@link Deliverer} parameter.  These strings will be the names of JSON
     * message parameters and will be mapped one-to-one to the corresponding
     * parameters of the method itself when it is invoked to handle a JSON
     * message.
     *
     * If a method is annotated {@link JSONMethod} but does not follow these
     * rules, no dispatch information will be recorded for that method and an
     * error message will be logged.
     *
     * @param targetClass  Class to compute method dispatch information for.
     *
     * @throws JSONSetupError if an annotated method breaks the rules for a
     *    JSON method.
     */
    public void addClass(Class targetClass) {
        if (!myClasses.contains(targetClass)) {
            for (Method method : targetClass.getMethods()) {
                JSONMethod note = method.getAnnotation(JSONMethod.class);

                if (note == null) {
                    continue;
                }

                if (!Modifier.isPublic(method.getModifiers())) {
                    throw new JSONSetupError("class " + targetClass.getName() +
                        " JSON message handler method " + method.getName() +
                        " is not public");
                }

                if (method.getReturnType() != void.class) {
                    throw new JSONSetupError("class " + targetClass.getName() +
                        " JSON message handler method " + method.getName() +
                        " does not have return type void");
                }

                Class paramTypes[] = method.getParameterTypes();
                if (paramTypes.length == 0 ||
                        !Deliverer.class.isAssignableFrom(paramTypes[0])) {
                    throw new JSONSetupError("class " + targetClass.getName() +
                        " JSON message handler method " + method.getName() +
                        " does not have a Deliverer first parameter");
                }

                String paramNames[] = note.value();
                if (paramNames.length + 1 != paramTypes.length) {
                    throw new JSONSetupError("class " + targetClass.getName() +
                        " JSON message handler method " + method.getName() +
                        " has wrong number of parameters");
                }
                String name = method.getName();
                MethodInvoker prev = myInvokers.get(name);
                myInvokers.put(name,
                    new MethodInvoker(method, paramTypes, paramNames, prev));
            }
            myClasses.add(targetClass);
        }
    }

    /**
     * Dispatch a received JSON message by invoking the appropriate JSON method
     * on the appropriate object with the parameters from the message.
     * This proceeds as follows:
     *
     * First, if 'from' is an instance of {@link SourceRetargeter}, then
     * 'from' is replaced with result of calling its {@link
     * SourceRetargeter#findEffectiveSource findEffectiveSource()} method.
     *
     * Second, if 'target' is an instance of {@link MessageRetargeter}, then
     * 'target' is replaced with the result of calling its {@link
     * MessageRetargeter#findActualTarget findActualTarget()} method.  This
     * step is repeated as many times as necessary until 'target' is no longer
     * an instance of {@link MessageRetargeter}.
     *
     * If 'target' has a method with the same name as the message verb in
     * 'message' and which matches the message handler signature pattern as
     * described in the description of the {@link #addClass addClass()} method,
     * then this method is invoked to handle the message and the message
     * dispatch operation is complete.  Note: for this to work, the 'target's
     * class must have previously been inserted into this dispatcher using the
     * {@link #addClass addClass()} method.
     *
     * If the previous step failed to located a message handler method, but
     * 'target' is an instance of {@link DefaultDispatchTarget}, then its
     * {@link DefaultDispatchTarget#handleMessage handleMessage()} method is
     * invoked to handle the message and the message dispatch operation is
     * complete.  Otherwise a {@link MessageHandlerException} is thrown.
     *
     * @param from  The source from whom the message was allegedly received.
     * @param target  The object to which the message is addressed.
     * @param message  The message itself.
     *
     * @throws MessageHandlerException if there was some kind of problem
     *    handling the message.
     */
    public void dispatchMessage(Deliverer from, DispatchTarget target,
                                JSONObject message)
        throws MessageHandlerException
    {
        String verb = message.verb();
        if (verb != null) {
            MethodInvoker invoker = myInvokers.get(verb);
            while (invoker != null) {
                DispatchTarget actualTarget = invoker.findActualTarget(target);
                if (actualTarget != null) {
                    if (from instanceof SourceRetargeter) {
                        from = ((SourceRetargeter) from).findEffectiveSource(
                            target);
                    }
                    if (from == null) {
                        throw new MessageHandlerException(
                            "invalid message target");
                    }
                    invoker.handle(actualTarget, from, message, myResolver);
                    return;
                } else {
                    invoker = invoker.next();
                }
            }
            if (target instanceof DefaultDispatchTarget) {
                DefaultDispatchTarget defaultTarget =
                    (DefaultDispatchTarget) target;
                if (from instanceof SourceRetargeter) {
                    from =
                        ((SourceRetargeter) from).findEffectiveSource(target);
                }
                defaultTarget.handleMessage(from, message);
            } else {
                throw new MessageHandlerException(
                    "no message handler method for verb '" + verb + "'");
            }
        } else {
            throw new MessageHandlerException("this message no verb");
        }
    }
}
