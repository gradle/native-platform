package net.rubygrapefruit.platform.file;

/**
 * A callback that is invoked whenever a path has changed.
 */
public interface FileWatcherCallback {
    /**
     * The given path has changed.
     *
     * See notes on the {@link FileWatcher} implementation for:
     *
     * <ul>
     *     <li>whether calls to this method can be expected from a single thread or multiple different ones,</li>
     *     <li>how actual events are reported.</li>
     * </ul>
     *
     * @param type the type of the change.
     * @param path the path of the change.
     *             For {@link Type#UNKNOWN} it can be {@code null}.
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
         * The metadata of an item with the given path has been modified.
         */
        METADATA_MODIFIED,

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
