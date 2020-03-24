package net.rubygrapefruit.platform.file;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A handle for watching file system locations.
 */
public interface FileWatcher extends Closeable {
    void startWatching(File... paths);

    void stopWatching(File... paths);

    /**
     * Stops watching and releases any native resources.
     * No more calls to the associated {@link FileWatcherCallback} will happen after this method returns.
     */
    @Override
    void close() throws IOException;
}
