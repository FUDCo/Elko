package org.elkoserver.server.context;

/**
 * Marker Interface for mods that may be attached to users.
 *
 * <p>The server will insist that any {@link Mod} subclass implement this
 * before it will allow it to be attached to a {@link User} object.
 */
public interface UserMod {
}
