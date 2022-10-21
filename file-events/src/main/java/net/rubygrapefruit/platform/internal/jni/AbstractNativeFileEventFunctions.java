package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.file.FileWatcher;

import java.io.File;
import java.util.Collection;

public abstract class AbstractNativeFileEventFunctions<W extends FileWatcher> extends AbstractFileEventFunctions<W> {
    public static String getVersion() {
        return getVersion0();
    }

    private static native String getVersion0();

    /**
     * Forces the native backend to drop the cached JUL log level and thus
     * re-query it the next time it tries to log something to the Java side.
     */
    public void invalidateLogLevelCache() {
        invalidateLogLevelCache0();
    }

    private native void invalidateLogLevelCache0();

    protected static abstract class NativeFileWatcher extends AbstractFileWatcher {
        protected final Object server;

        public NativeFileWatcher(Object server, NativeFileWatcherCallback callback) {
            super(callback);
            this.server = server;
        }

        @Override
        protected void initializeRunLoop() {
            initializeRunLoop0(server);
        }

        private native void initializeRunLoop0(Object server);

        @Override
        protected void executeRunLoop() {
            executeRunLoop0(server);
        }

        private native void executeRunLoop0(Object server);

        @Override
        protected void doStartWatching(Collection<File> paths) {
            startWatching0(server, toAbsolutePaths(paths));
        }

        private native void startWatching0(Object server, String[] absolutePaths);

        @Override
        protected boolean doStopWatching(Collection<File> paths) {
            return stopWatching0(server, toAbsolutePaths(paths));
        }

        private native boolean stopWatching0(Object server, String[] absolutePaths);

        protected static String[] toAbsolutePaths(Collection<File> files) {
            String[] paths = new String[files.size()];
            int index = 0;
            for (File file : files) {
                paths[index++] = file.getAbsolutePath();
            }
            return paths;
        }

        @Override
        protected void doShutdown() {
            shutdown0(server);
        }

        private native void shutdown0(Object server);

        @Override
        protected boolean awaitTermination(long timeoutInMillis) {
            return awaitTermination0(server, timeoutInMillis);
        }

        private native boolean awaitTermination0(Object server, long timeoutInMillis);
    }
}
