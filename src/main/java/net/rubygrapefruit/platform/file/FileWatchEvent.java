package net.rubygrapefruit.platform.file;

import javax.annotation.Nullable;

public interface FileWatchEvent {

    void handleEvent(Handler handler);

    interface Handler {
        void handleChangeEvent(ChangeType type, String absolutePath);

        void handleUnknownEvent(String absolutePath);

        void handleOverflow(OverflowType type, @Nullable String absolutePath);

        void handleFailure(Throwable failure);

        void handleTerminated();
    }

    enum ChangeType {
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
        INVALIDATED
    }

    enum OverflowType {
        /**
         * The overflow happened in the operating system's routines.
         */
        OPERATING_SYSTEM,

        /**
         * The overflow happened because the Java event queue has filled up.
         */
        EVENT_QUEUE
    }
}
