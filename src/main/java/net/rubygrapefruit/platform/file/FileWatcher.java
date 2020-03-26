package net.rubygrapefruit.platform.file;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * A handle for watching file system locations.
 */
public interface FileWatcher extends Closeable {
    void startWatching(Collection<File> paths);

    boolean stopWatching(Collection<File> paths);

    /**
     * Stops watching and releases any native resources.
     * No more events will arrive after this method returns.
     */
    @Override
    void close() throws IOException;
}
