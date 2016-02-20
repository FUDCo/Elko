package org.elkoserver.foundation.json;

/**
 * Interface for an object that can be a source of JSON messages on behalf of
 * other objects.  This provides a linkage between the object that actually
 * pulls the message off of a network connection and the object that the
 * message is represented to its receiver as being from.  In other words, this
 * interface gives the object that does the actual work of receiving a message
 * the opportunity to substitute some object other than itself as the 'from'
 * parameter to the message handler.
 *
 * <p>Compare and contrast with {@link MessageRetargeter}.
 */
public interface SourceRetargeter {
    /**
     * Designate an object that should be treated as the source of a message
     * instead of this object.
     *
     * @param target  The object to which the message is addressed.
     *
     * @return an object that should be presented to the message handler as the
     *    source of a message to 'target' in place of this object.
     */
    Deliverer findEffectiveSource(DispatchTarget target);
}
