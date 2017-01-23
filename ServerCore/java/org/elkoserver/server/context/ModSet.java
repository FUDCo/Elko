package org.elkoserver.server.context;

import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteralArray;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Collection class to hold all the mods attached to a basic object.
 *
 * An object only acquires a ModSet if it actually has mods attached.
 */
class ModSet {
    /** The mods themselves, indexed by class. */
    private Map<Class, Mod> myMods;

    /** Auxiliary mods table, to lookup mods by superclass. */
    private Map<Class, Mod> mySuperMods;

    /**
     * Constructor.  Note that this is private so it won't be called
     * externally.  The proper API for other objects to use is the 'withMod'
     * static method.
     */
    private ModSet() {
        myMods = new HashMap<Class, Mod>();
        mySuperMods = null;
    }

    /**
     * Constructor.
     *
     * @param mods  Array of mods to generate the mod set from.
     */
    public ModSet(Mod mods[]) {
        this();
        if (mods != null) {
            for (Mod mod : mods) {
                addMod(mod);
            }
        }
    }

    /**
     * Add a mod to the set.
     *
     * @param mod  The mod to add.
     */
    private void addMod(Mod mod) {
        Class modClass = mod.getClass();
        myMods.put(modClass, mod);
        modClass = modClass.getSuperclass();
        while (modClass != Mod.class && Mod.class.isAssignableFrom(modClass)) {
            if (mySuperMods == null) {
                mySuperMods = new HashMap<Class, Mod>();
            }
            mySuperMods.put(modClass, mod);
            modClass = modClass.getSuperclass();
        } 
    }

    /**
     * Make all these mods become mods of something.
     *
     * @param object  The object to which these mods are to be attached.
     */
    public void attachTo(BasicObject object) {
        for (Mod mod : myMods.values()) {
            mod.attachTo(object);
        }
    }

    /**
     * Encode this mods list as a JSONLiteralArray object.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSONLiteralArray object representing this mods list.
     */
    JSONLiteralArray encode(EncodeControl control) {
        JSONLiteralArray result = new JSONLiteralArray(control);
        for (Mod mod : myMods.values()) {
            if (control.toClient() || !mod.isEphemeral()) {
                result.addElement(mod);
            }
        }
        result.finish();
        return result;
    }

    /**
     * Get a mod from the set, if there is one.
     *
     * @param type  The class of mod sought.
     *
     * @return the mod of the given class, or null if there is no such mod.
     */
    Mod getMod(Class type) {
        Mod result = myMods.get(type);
        if (result == null && mySuperMods != null) {
            result = mySuperMods.get(type);
        }
        return result;
    }

    /**
     * Arrange to inform any mods that have expressed an interest that the
     * object they are mod of is now complete.  If the mod is a
     * ContextShutdownWatcher, automatically register interest in the shutdown
     * event with the context.
     */
    public void objectIsComplete() {
        for (Mod mod : myMods.values()) {
            if (mod instanceof ObjectCompletionWatcher) {
                mod.object().contextor().addPendingObjectCompletionWatcher(
                    (ObjectCompletionWatcher) mod);
            }
            if (mod instanceof ContextShutdownWatcher) {
                mod.context().registerContextShutdownWatcher(
                    (ContextShutdownWatcher) mod);
            }
        }
    }

    /**
     * Remove from the mods list any mods that are marked as being ephemeral.
     */
    void purgeEphemeralMods() {
        Iterator<Mod> iter = myMods.values().iterator();
        while (iter.hasNext()) {
            Mod mod = iter.next();
            if (mod.isEphemeral()) {
                iter.remove();
            }
        }
    }

    /**
     * Add a mod to a set, creating the set if necessary to do so.
     *
     * @param modSet  The set to which the mod is to be added, or null if no
     *    set exists yet.
     * @param mod  The mod to add.
     *
     * @return the set (created if necessary), with 'mod' in it.
     */
    static ModSet withMod(ModSet modSet, Mod mod) {
        if (modSet == null) {
            modSet = new ModSet();
        }
        modSet.addMod(mod);
        return modSet;
    }
}
