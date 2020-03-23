package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;

import java.io.File;

public class AbstractFileEventFunctions implements NativeIntegration {
    public static native String getVersion();

    /**
     * Forces the native backend to drop the cached JUL log level and thus
     * re-query it the next time it tries to log something to the Java side.
     */
    public native void invalidateLogLevelCache();

    protected static class NativeFileWatcherCallback {
        private final FileWatcherCallback delegate;

        public NativeFileWatcherCallback(FileWatcherCallback delegate) {
            this.delegate = delegate;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void pathChanged(int type, String path) {
            delegate.pathChanged(FileWatcherCallback.Type.values()[type], path);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportError(Throwable ex) {
            delegate.reportError(ex);
        }
    }

    // Instantiated from native code
    @SuppressWarnings("unused")
    protected static class NativeFileWatcher implements FileWatcher {
        /**
         * A Java object wrapper around the native server object.
         */
        private Object server;

        public NativeFileWatcher(Object server) {
            this.server = server;
        }

        @Override
        public void startWatching(File path) {
            if (server == null) {
                throw new IllegalStateException("Watcher already closed");
            }
            startWatching(server, path.getAbsolutePath());
        }

        private native void startWatching(Object server, String absolutePath);

        @Override
        public void stopWatching(File path) {
            if (server == null) {
                throw new IllegalStateException("Watcher already closed");
            }
            stopWatching(server, path.getAbsolutePath());
        }

        private native void stopWatching(Object server, String absolutePath);

        @Override
        public void close() {
            if (server == null) {
                throw new NativeException("Closed already");
            }
            stop(server);
            server = null;
        }

        protected native void stop(Object details);
    }
}
