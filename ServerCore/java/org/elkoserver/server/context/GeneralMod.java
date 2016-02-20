package org.elkoserver.server.context;

/**
 * Marker Interface for mods that may be attached to any object.
 *
 * <p>This interface is just a notational convenience for coding {@link Mod}
 * subclasses that can be attached to anything.
 */
public interface GeneralMod extends ContextMod, ItemMod, UserMod {
}
