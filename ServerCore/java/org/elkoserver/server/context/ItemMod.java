package org.elkoserver.server.context;

/**
 * Marker Interface for mods that may be attached to items.
 *
 * <p>The server will insist that any {@link Mod} subclass implement this
 * before it will allow it to be attached to a {@link Item} object.
 */
public interface ItemMod {
}
