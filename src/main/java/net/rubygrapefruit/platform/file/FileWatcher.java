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
     * Closes the watcher and releases any native resources.
     */
    @Override
    void close() throws IOException;
}
