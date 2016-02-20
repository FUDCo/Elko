package org.elkoserver.server.context.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.GeneralMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import java.util.HashMap;
import java.util.Map;

/**
 * Mod to associate a server-moderated hashtable with its object.  This mod
 * may be attached to a context, user or item.
 */
public class Dictionary extends Mod implements GeneralMod {
    /* Persistent state, initialized from database. */

    /** The variables themselves, map of names to values. */
    private Map<String, String> myVars;

    /** True if changes mark parent object as dirty. */
    private boolean amPersistent;

    /** Original name->values mapping in the case of non-persistence. */
    private Map<String, String> myOriginalVars;

    /**
     * JSON-driven constructor.
     *
     * @param names  Array of variable names.
     * @param values  Parallel array of values for those variables.
     * @param persist  If true, make sure any changes get saved to disk; if
     *    false (the default), changes are ephemeral.
     */
    @JSONMethod({ "names", "values", "persist" })
    public Dictionary(String names[], String values[], OptBoolean persist) {
        int nameCount = 0;
        
        amPersistent = persist.value(false);
        
        if (names != null) {
            nameCount = names.length;
        }
        myVars = new HashMap<String, String>(nameCount);
        for (int i = 0; i < nameCount; ++i) {
            myVars.put(names[i], values[i]);
        }
        if (amPersistent) {
            myOriginalVars = null;
        } else {
            myOriginalVars = new HashMap<String, String>(nameCount, 1.0f);
            for (int i = 0; i < nameCount; ++i) {
                myOriginalVars.put(names[i], values[i]);
            }
        }
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this mod.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("dictionary", control);
        Map<String, String> vars;
        if (control.toClient() || amPersistent) {
            vars = myVars;
        } else {
            vars = myOriginalVars;
        }
        int size = vars.size();
        String names[] = new String[size];
        String values[] = new String[size];
        int i = 0;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            names[i] = entry.getKey();
            values[i] = entry.getValue();
            ++i;
        }
        result.addParameter("names", names);
        result.addParameter("values", values);
        if (control.toRepository() && amPersistent) {
            result.addParameter("persist", amPersistent);
        }
        result.finish();
        return result;
    }

    /**
     * Message handler for the 'delvar' message.
     *
     * <p>This message is a request to delete of one or more of the variables
     * from the set.  If the operation is successful, a corresponding 'delvar'
     * message is broadcast to the context.
     *
     * <p>Warning: This message is not secure.  As implemented today, anyone
     * can delete variables.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"delvar",
     *                     names:<i>STR[]</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"delvar",
     *                     names:<i>STR[[</i> } </tt>
     *
     * @param from  The user who sent the message.
     * @param names  Names of the variables to remove.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod.
     */
    @JSONMethod({ "names" })
    public void delvar(User from, String names[])
        throws MessageHandlerException
    {
        ensureSameContext(from);
        for (String name : names) {
            myVars.remove(name);
        }
        if (amPersistent) {
            markAsChanged();
        }
        context().send(msgDelvar(object(), from, names));
    }

    /**
     * Message handle for the 'setvar' message.
     *
     * <p>This message is a request to change the value of one or more of the
     * variables (or to assign a new variable).  If the operation is
     * successfull, a corresponding 'setvar' message is broadcast to the
     * context.
     *
     * <p>Warning: This message is not secure.  As implemented today, anyone
     * can modify variables.<p>
     *
     * <u>recv</u>: <tt> { to:<i>REF</i>, op:"setvar", names:<i>STR[]</i>,
     *                     values:<i>STR[]</i> } </tt><br>
     *
     * <u>send</u>: <tt> { to:<i>REF</i>, op:"setvar", names:<i>STR[]</i>,
     *                     values:<i>STR[]</i> } </tt>
     *
     * @param from  The user who sent the message.
     * @param names  Names of the variables to assign.
     * @param values  The values to set them to.  Each element of the array is
     *    the value for the corresponding element of 'names.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     *    this mod.
     */
    @JSONMethod({ "names", "values" })
    public void setvar(User from, String names[], String values[])
        throws MessageHandlerException
    {
        ensureSameContext(from);
        if (names.length != values.length) {
            throw new MessageHandlerException(
                "parameter array lengths unequal");
        }
        for (int i = 0; i < names.length; ++i) {
            myVars.put(names[i], values[i]);
        }
        if (amPersistent) {
            markAsChanged();
        }
        context().send(msgSetvar(object(), from, names, values));
    }

    /**
     * Create a 'delvar' message.
     *
     * @param target  Object the message is being sent to.
     * @param from  Object the message is to be alleged to be from, or null if
     *    not relevant.
     * @param names  Names of the variables to delete.
     */
    static JSONLiteral msgDelvar(Referenceable target, Referenceable from,
                                 String names[])
    {
        JSONLiteral msg = new JSONLiteral(target, "delvar");
        msg.addParameterOpt("from", from);
        msg.addParameter("names", names);
        msg.finish();
        return msg;
    }

    /**
     * Create a 'setvar' message.
     *
     * @param target  Object the message is being sent to.
     * @param from  Object the message is to be alleged to be from, or null if
     *    not relevant.
     * @param names  Names of variables to change.
     * @param values  The values to change them to.
     */
    static JSONLiteral msgSetvar(Referenceable target, Referenceable from,
                                 String names[], String values[])
    {
        JSONLiteral msg = new JSONLiteral(target, "setvar");
        msg.addParameterOpt("from", from);
        msg.addParameter("names", names);
        msg.addParameter("values", values);
        msg.finish();
        return msg;
    }
}
