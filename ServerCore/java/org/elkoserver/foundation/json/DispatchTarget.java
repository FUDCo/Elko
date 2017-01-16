package org.elkoserver.foundation.json;

/**
 * Marker interface for objects that can be the recipients of JSON messages.
 *
 * This interface has no method protocol.  Instead, classes "implementing" this
 * interface must mark individual message handler methods using the {@link
 * JSONMethod} attribute.  These are invoked (by means of the Java reflection
 * API) by the JSON message handler dispatch mechanism.
 *
 * <p>Classes you write that handle JSON messages won't generally need to
 * declare themselves as implementing this interface, since the normal coding
 * pattern for such things is to subclass a standard base class (such as {@link
 * org.elkoserver.server.context.BasicObject BasicObject}, {@link
 * org.elkoserver.server.context.Mod Mod},
 * {@link org.elkoserver.foundation.actor.BasicProtocolHandler BasicProtocolHandler}, or
 * {@link org.elkoserver.foundation.actor.NonRoutingActor NonRoutingActor})
 * that already implements it.
 */
public interface DispatchTarget {
}
