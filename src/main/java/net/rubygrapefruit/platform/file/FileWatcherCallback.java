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

    enum Type {
        /**
         * An item with the given path has been created.
         */
        CREATED,
        /**
         * An item with the given path has been removed.
         */
        REMOVED,
        /**
         * An item with the given path has been modified.
         */
        MODIFIED,
        /**
         * Some undisclosed changes happened under the given path,
         * all information about descendants must be discarded.
         */
        INVALIDATE,
        /**
         * An unknown event happened to the given path or some of its descendants,
         * discard all information about the file system.
         */
        UNKNOWN
    }
}
