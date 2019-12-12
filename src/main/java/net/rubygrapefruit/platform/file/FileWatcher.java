package net.rubygrapefruit.platform.file;

import java.io.Closeable;
import java.io.IOException;

/**
 * A handle for watching file system locations.
 */
public interface FileWatcher extends Closeable {
    FileWatcher EMPTY = new FileWatcher() {
        @Override
        public void close() {}
    };

    /**
     * Stops watching and releases any native resources.
     * No more calls to the associated {@link FileWatcherCallback} will happen after this method returns.
     */
    @Override
    void close() throws IOException;
}
