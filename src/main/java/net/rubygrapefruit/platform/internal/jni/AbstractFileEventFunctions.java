package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class AbstractFileEventFunctions implements NativeIntegration {
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

    public abstract static class AbstractWatcherBuilder {
        protected final FileWatcherCallback callback;

        public AbstractWatcherBuilder(FileWatcherCallback callback) {
            this.callback = callback;
        }

        /**
         * Start the file watcher.
         *
         * @see FileWatcher#startWatching(Collection)
         */
        public abstract FileWatcher start() throws InterruptedException;
    }

    /**
     * Configures a new watcher using a builder. Call {@link AbstractWatcherBuilder#start()} to
     * actually start the {@link FileWatcher}.
     */
    public abstract AbstractWatcherBuilder newWatcher(FileWatcherCallback callback);

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

    protected static class NativeFileWatcher implements FileWatcher {
        private final Object server;
        private final Thread processorThread;
        private boolean closed;

        public NativeFileWatcher(final Object server) throws InterruptedException {
            this.server = server;
            final CountDownLatch runLoopInitialized = new CountDownLatch(1);
            this.processorThread = new Thread("File watcher server") {
                @Override
                public void run() {
                    initializeRunLoop0(server);
                    runLoopInitialized.countDown();
                    executeRunLoop0(server);
                }
            };
            this.processorThread.setDaemon(true);
            this.processorThread.start();
            // TODO Parametrize this
            runLoopInitialized.await(5, TimeUnit.SECONDS);
        }

        private native void initializeRunLoop0(Object server);

        private native void executeRunLoop0(Object server);

        @Override
        public void startWatching(Collection<File> paths) {
            ensureOpen();
            startWatching0(server, toAbsolutePaths(paths));
        }

        private native void startWatching0(Object server, String[] absolutePaths);

        @Override
        public boolean stopWatching(Collection<File> paths) {
            ensureOpen();
            return stopWatching0(server, toAbsolutePaths(paths));
        }

        private native boolean stopWatching0(Object server, String[] absolutePaths);

        private static String[] toAbsolutePaths(Collection<File> files) {
            String[] paths = new String[files.size()];
            int index = 0;
            for (File file : files) {
                paths[index++] = file.getAbsolutePath();
            }
            return paths;
        }

        @Override
        public void close() throws InterruptedIOException {
            ensureOpen();
            closed = true;
            close0(server);
            processorThread.interrupt();
            // TODO Parametrize timeout here
            try {
                processorThread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.getMessage());
            }
        }

        // Rename this to terminate0() maybe?
        protected native void close0(Object server);

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Watcher already closed");
            }
        }
    }
}
