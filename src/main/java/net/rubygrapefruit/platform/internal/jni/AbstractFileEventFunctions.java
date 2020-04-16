package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

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
        protected final BlockingQueue<FileWatchEvent> eventQueue;

        public AbstractWatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            this.eventQueue = eventQueue;
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
    public abstract AbstractWatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> queue);

    protected static class NativeFileWatcherCallback {

        private final BlockingQueue<FileWatchEvent> eventQueue;

        public NativeFileWatcherCallback(BlockingQueue<FileWatchEvent> eventQueue) {
            this.eventQueue = eventQueue;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void pathChanged(int typeIndex, String path) throws InterruptedException {
            FileWatchEvent.Type type = FileWatchEvent.Type.values()[typeIndex];
            if (type == FileWatchEvent.Type.OVERFLOWED) {
                signalOverflow(path);
            } else {
                queueEvent(new ChangeEvent(type, path), false);
            }
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportError(Throwable ex) throws InterruptedException {
            queueEvent(new ErrorEvent(ex), true);
        }

        private void queueEvent(FileWatchEvent event, boolean deliverOnOverflow) throws InterruptedException {
            // TODO Make the timeout configurable
            if (!eventQueue.offer(event, 1, SECONDS)) {
                NativeLogger.LOGGER.info("Event queue overflow, dropping all events");
                signalOverflow(null);
                if (deliverOnOverflow) {
                    eventQueue.put(event);
                }
            }
        }

        private void signalOverflow(@Nullable String path) throws InterruptedException {
            eventQueue.clear();
            eventQueue.put(new ChangeEvent(FileWatchEvent.Type.OVERFLOWED, path));
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
            runLoopInitialized.await(5, SECONDS);
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
                processorThread.join(SECONDS.toMillis(5));
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

    private static class ChangeEvent implements FileWatchEvent {
        private final Type type;
        private final String path;

        public ChangeEvent(Type type, String path) {
            this.type = type;
            this.path = path;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return null;
        }

        @Override
        public String toString() {
            return type + " " + path;
        }
    }

    private static class ErrorEvent implements FileWatchEvent {
        private final Throwable failure;

        public ErrorEvent(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public Type getType() {
            return Type.FAILURE;
        }

        @Nullable
        @Override
        public String getPath() {
            return null;
        }

        @Override
        public Throwable getFailure() {
            return failure;
        }

        @Override
        public String toString() {
            return Type.FAILURE + " " + failure.getMessage();
        }
    }
}
