package org.elkoserver.server.context;

/**
 * Marker Interface for mods that may be attached to contexts.
 *
 * <p>The server will insist that any {@link Mod} subclass implement this
 * before it will allow it to be attached to a {@link Context} object.
 */
public interface ContextMod {
}
