package net.rubygrapefruit.platform.file;

import javax.annotation.Nullable;

public interface FileWatchEvent {

    /**
     * The type of the change. When {@link Type#FAILURE}, {@link #getFailure()} returns
     * the failure, and {@link #getPath()} returns {@code null}.
     * Otherwise {@link #getFailure()} returns {@code null} and {@link #getPath()}
     * returns either the path of the change, or {@code null} if a path is not known.
     */
    Type getType();

    /**
     * The path that has been changed. Can be {@code null} when {@link #getType()} is
     * {@link Type#FAILURE} or {@link Type#UNKNOWN}.
     *
     * See notes on the {@link FileWatcher} implementation for:
     *
     * <ul>
     *     <li>whether calls to this method can be expected from a single thread or multiple different ones,</li>
     *     <li>how actual events are reported.</li>
     * </ul>
     */
    @Nullable
    String getPath();

    /**
     * Returns the failure encountered when {@link #getType()} is {@link Type#FAILURE}, otherwise {@code null}.
     */
    @Nullable
    Throwable getFailure();

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
        INVALIDATED,

        /**
         * An overflow happened, all information about descendants must be discarded.
         */
        OVERFLOWED,

        /**
         * An unknown event happened to the given path or some of its descendants,
         * discard all information about the file system.
         */
        UNKNOWN,

        /**
         * An error happened to the given path or some of its descendants.
         *
         * @see FileWatchEvent#getFailure()
         */
        FAILURE
    }
}
