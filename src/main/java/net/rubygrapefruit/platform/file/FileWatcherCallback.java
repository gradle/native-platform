package net.rubygrapefruit.platform.file;

/**
 * A callback that is invoked whenever a path has changed.
 */
public interface FileWatcherCallback {
    /**
     * The given path has changed.
     */
    // Invoked from native code
    @SuppressWarnings("unused")
    void pathChanged(Type type, String path);

    /**
     * The file watcher has hit a wall and cannot report all changes, must invalidate everything.
     */
    // Invoked from native code
    @SuppressWarnings("unused")
    void overflow();

    enum Type {
        ADDED,
        REMOVED,
        MODIFIED,
        CHILDREN_CHANGED,
        DESCENDANTS_CHANGED
    }
}
