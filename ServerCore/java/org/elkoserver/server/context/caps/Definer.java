package org.elkoserver.server.context.caps;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.context.BasicObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserMod;

/**
 * Capability to enable external definition of persistent C-U-I objects.
 */
public class Definer extends Cap implements ItemMod, UserMod {

    /** Reference strings for contexts this capability enables entry to. */
    private String myContexts[];

    /**
     * JSON-driven constructor.
     */
    @JSONMethod
    public Definer(JSONObject raw) throws JSONDecodingException {
        super(raw);
    }

    /**
     * Message handler for a 'define' message.  This is a request to define a
     * new context, item, or user.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"define", into:<i>optREF</i>,
     *                     ref:<i>optREF</i>, obj:<i>OBJDESC</i> }</tt><br>
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"define", ref:<i>REF</i> } </tt>
     *
     * @param into  Container into which the new object should be placed
     *    (optional, defaults to no container).
     * @param ref  Reference string for the new object (optional, defaults to
     *    an automatically generated ref).
     * @param obj  JSON descriptor for the object itself
     *
     * @throws MessageHandlerException if 'from' is not the holder of this
     *    definer capability, or if an explicit ID is given but an object of
     *    that ID is loaded, or if a container is given and that container
     *    object is loaded, or if the object descriptor is not a valid context,
     *    item or user descriptor.
     */
    @JSONMethod({ "into", "ref", "obj" })
    public void define(User from, OptString into, OptString ref,
                       BasicObject obj)
        throws MessageHandlerException
    {
        ensureReachable(from);
        String intoRef = into.value(null);
        if (intoRef != null) {
            if (context().get(intoRef) != null) {
                throw new MessageHandlerException("container " + intoRef +
                                                  " is loaded");
            }
        }
        Contextor contextor = object().contextor();
        String newRef = ref.value(null);
        if (newRef != null) {
            if (context().get(newRef) != null) {
                throw new MessageHandlerException("proposed ref " + newRef +
                                                  " is loaded");
            }
        }
        contextor.createObjectRecord(newRef, intoRef, obj);
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("definer", control);
        encodeDefaultParameters(result);
        result.finish();
        return result;
    }
}
