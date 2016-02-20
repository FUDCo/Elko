package org.elkoserver.foundation.json;

/**
 * Interface for an object to redirect JSON messages targeted at it.
 *
 * <p>When the message dispatch mechanism attempts to deliver a message to an
 * object, if the object implements this interface the server will instead call
 * the {@link #findActualTarget findActualTarget()} method and attempt to
 * deliver the message to the object thus returned.  This redirection can be
 * applied recursively (i.e., the new target may itself implement this
 * interface).
 *
 * <p>Compare and contrast with {@link SourceRetargeter}.
 */
public interface MessageRetargeter {
    /**
     * Return the object that should actually receive a message in place of
     * this object.
     *
     * @param type  The class for which the message might have been intended.
     *
     * @return an object that can handle messages for class 'type' on behalf of
     *    this object, or null if no such object exists.
     */
    DispatchTarget findActualTarget(Class type);
}
