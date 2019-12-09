package net.rubygrapefruit.platform.file;

/**
 * A callback that is invoked whenever a path has changed.
 */
public interface FileWatcherCallback {
    // Invoked from native code
    @SuppressWarnings("unused")
    void pathChanged(String path);
}
