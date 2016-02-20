package org.elkoserver.server.context;

/**
 * Interface for capabilities that enable entry to entry controlled contexts.
 */
public interface ContextKey {
    /**
     * Test if this key enables entry to a particular context.
     *
     * @param contextRef  Reference string of the context of interest.
     *
     * @return true if this key enables entry to the context designated
     *    by 'contextRef', false if not.
     */
    public boolean enablesEntry(String contextRef);
}
