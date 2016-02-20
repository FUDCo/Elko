package org.elkoserver.server.context;

/**
 * Interface implemented by mods that wish to be notified when an item is
 * added to or removed from the container they are attached to.
 *
 * <p>To enable this notification, mods may implement this interface, though
 * only one mod per object may implement it.
 *
 * <p>This interface is only useful when implemented by subclasses of {@link
 * Mod} that are attached to container objects of some kind.
 */
public interface ContentsWatcher {
    /**
     * Do whatever you want when an item is added to the container.
     *
     * @param what  The item that was added.
     */
    void noteContentsAddition(Item what);

    /**
     * Do whatever you want when an item is removed from the container.
     *
     * @param what  The item that was removed.
     */
    void noteContentsRemoval(Item what);
}
