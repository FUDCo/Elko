package org.elkoserver.server.context;

/**
 * Interface implemented by mods that wish to be notified when their object
 * creation is finished.
 *
 * <p>Creating a {@link Context}, {@link User}, or {@link Item} object by the
 * context server may entail the creation of one or more mod objects (i.e.,
 * instances of some subclass of {@link Mod}) that are attached to it.
 * Sometimes a mod's initialization requires access to the object it is
 * attached to or to other mods attached to that same object.  However, when a
 * mod's constructor is called, the mod is not yet attached to the object.
 * Consequently, information about the mod's object environment is not
 * available to the mod constructor.
 *
 * <p>To enable initialization operations based on the object environment, mods
 * may implement this interface.  Once all of the mods associated with an
 * object have been created and attached to it, and the object has been plugged
 * into its containership hierarchy, the server will invoke the {@link
 * #objectIsComplete} method of each of that object's mods that implement this
 * interface.
 *
 * <p>This interface is only useful when implemented by subclasses of {@link
 * Mod}.
 *
 * <p>IMPORTANT NOTE: Implementors of this interface MUST not depend on the
 * relative ordering of calls to {@link #objectIsComplete} on different mods to
 * the same object, nor those of different objects being loaded at the same
 * time as part of a common containership hierarchy.
 */
public interface ObjectCompletionWatcher {
    /**
     * Do what needs to be done now that you are in a finished object
     * environment.
     *
     * <p>This method will be called automatically by the server's object
     * creation mechanism upon completion of the creation of an object to which
     * the mod implementing this interface is attached.
     *
     * <p>Note: if an object has more than one mod that implements this
     * interface, the order in which the various mods' implementations of this
     * method will be called is undefined.
     */
    void objectIsComplete();
}
