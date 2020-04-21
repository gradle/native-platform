package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatchEvent.OverflowType;
import net.rubygrapefruit.platform.file.FileWatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

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

        /**
         * Starts the file watcher with a thread consuming the events and
         * feeding them to the given {@link FileWatchEvent.Handler}.
         *
         * The started thread will terminate once the returned
         * {@link FileWatcher} is {@link FileWatcher#close() closed}.
         */
        public FileWatcher startWithHandler(FileWatchEvent.Handler handler) throws InterruptedException {
            return startWithHandler(new ThreadConfigurator() {
                @Override
                public void configure(Thread processorThread) {
                    // Do nothing
                }
            }, handler);
        }

        /**
         * Starts the file watcher with a thread consuming the events and
         * feeding them to the given {@link FileWatchEvent.Handler}.
         *
         * The started thread will terminate once the returned
         * {@link FileWatcher} is {@link FileWatcher#close() closed},
         * or when it is {@link Thread#interrupt() interrupted}.
         *
         * Allows the configuration of the thread created via the given
         * {@link ThreadConfigurator} before it is started.
         */
        public FileWatcher startWithHandler(ThreadConfigurator threadConfigurator, final FileWatchEvent.Handler handler) throws InterruptedException {
            Thread processorThread = new Thread(new Runnable() {
                private volatile boolean terminated;

                @Override
                public void run() {
                    while (!terminated && !Thread.interrupted()) {
                        FileWatchEvent event;
                        try {
                            event = eventQueue.take();
                        } catch (InterruptedException e) {
                            break;
                        }
                        event.handleEvent(new FileWatchEvent.Handler() {
                            @Override
                            public void handleChangeEvent(FileWatchEvent.ChangeType type, String absolutePath) {
                                handler.handleChangeEvent(type, absolutePath);
                            }

                            @Override
                            public void handleUnknownEvent(String absolutePath) {
                                handler.handleUnknownEvent(absolutePath);
                            }

                            @Override
                            public void handleOverflow(FileWatchEvent.OverflowType type, String absolutePath) {
                                handler.handleOverflow(type, absolutePath);
                            }

                            @Override
                            public void handleFailure(Throwable failure) {
                                handler.handleFailure(failure);
                            }

                            @Override
                            public void handleTerminated() {
                                terminated = true;
                                handler.handleTerminated();
                            }
                        });
                    }
                }
            }, "File watcher event handler");
            threadConfigurator.configure(processorThread);
            processorThread.start();
            return start();
        }
    }

    interface ThreadConfigurator {
        void configure(Thread processorThread);
    }

    /**
     * Configures a new watcher using a builder.
     * Call {@link AbstractWatcherBuilder#start()} to actually start the {@link FileWatcher}.
     *
     * The queue must have a total capacity of at least 2 elements.
     * The caller should only consume events from the queue, and never add any of their own.
     */
    public abstract AbstractWatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> queue);

    enum EventType {
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
        INVALIDATED,

        /**
         * An overflow happened, all information about descendants must be discarded.
         */
        OVERFLOWED,

        /**
         * An unknown event happened to the given path or some of its descendants,
         * discard all information about the file system.
         */
        UNKNOWN
    }

    protected static class NativeFileWatcherCallback {

        private final BlockingQueue<FileWatchEvent> eventQueue;

        public NativeFileWatcherCallback(BlockingQueue<FileWatchEvent> eventQueue) {
            this.eventQueue = eventQueue;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void pathChanged(int typeIndex, String path) {
            EventType type = EventType.values()[typeIndex];
            switch (type) {
                case OVERFLOWED:
                    signalOverflow(OverflowType.OPERATING_SYSTEM, path);
                    break;
                case UNKNOWN:
                    queueEvent(new UnknownEvent(path), false);
                    break;
                default:
                    FileWatchEvent.ChangeType changeType;
                    switch (type) {
                        case CREATED:
                            changeType = FileWatchEvent.ChangeType.CREATED;
                            break;
                        case MODIFIED:
                            changeType = FileWatchEvent.ChangeType.MODIFIED;
                            break;
                        case REMOVED:
                            changeType = FileWatchEvent.ChangeType.REMOVED;
                            break;
                        case INVALIDATED:
                            changeType = FileWatchEvent.ChangeType.INVALIDATED;
                            break;
                        default:
                            throw new AssertionError();
                    }
                    queueEvent(new ChangeEvent(changeType, path), false);
                    break;
            }
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportError(Throwable ex) {
            queueEvent(new FailureEvent(ex), true);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportTermination() {
            queueEvent(TerminationEvent.INSTANCE, true);
        }

        private void queueEvent(FileWatchEvent event, boolean deliverOnOverflow) {
            if (!eventQueue.offer(event)) {
                NativeLogger.LOGGER.info("Event queue overflow, dropping all events");
                signalOverflow(OverflowType.EVENT_QUEUE, null);
                if (deliverOnOverflow) {
                    forceQueueEvent(event);
                }
            }
        }

        private void signalOverflow(OverflowType type, @Nullable String path) {
            eventQueue.clear();
            forceQueueEvent(new OverflowEvent(type, path));
        }

        /**
         * Queue event to a queue that we expect has enough capacity to accept the event.
         * We expect there is enough space because we just cleared the queue, and thus
         * it should have enough space.
         *
         * This can fail if the queue is extremely small (has 0 capacity, or has a capacity of
         * 1 and we are trying to queue an error event here right after an overflow event).
         * The queue can also be full if some other thread is adding events to it.
         * Both a queue with a less than two element capacity and pushing events from user code
         * are forbidden. If they occur the best we can do is log the situation.
         */
        private void forceQueueEvent(FileWatchEvent event) {
            boolean eventPublished = eventQueue.offer(event);
            if (!eventPublished) {
                NativeLogger.LOGGER.severe("Couldn't queue event: " + event);
            }
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
        private final ChangeType type;
        private final String path;

        public ChangeEvent(ChangeType type, String path) {
            this.type = type;
            this.path = path;
        }

        @Override
        public void handleEvent(Handler handler) {
            handler.handleChangeEvent(type, path);
        }

        @Override
        public String toString() {
            return type + " " + path;
        }
    }

    private static class OverflowEvent implements FileWatchEvent {
        private final OverflowType type;
        private final String path;

        public OverflowEvent(OverflowType type, @Nullable String path) {
            this.type = type;
            this.path = path;
        }

        @Override
        public void handleEvent(Handler handler) {
            handler.handleOverflow(type, path);
        }

        @Override
        public String toString() {
            return "OVERFLOW (" + type + ") at " + path;
        }
    }

    private static class UnknownEvent implements FileWatchEvent {
        private final String path;

        public UnknownEvent(String path) {
            this.path = path;
        }

        @Override
        public void handleEvent(Handler handler) {
            handler.handleUnknownEvent(path);
        }

        @Override
        public String toString() {
            return "UNKNOWN " + path;
        }
    }

    private static class FailureEvent implements FileWatchEvent {
        private final Throwable failure;

        public FailureEvent(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void handleEvent(Handler handler) {
            handler.handleFailure(failure);
        }

        @Override
        public String toString() {
            return "FAILURE " + failure.getMessage();
        }
    }

    private static class TerminationEvent implements FileWatchEvent {
        public static final FileWatchEvent INSTANCE = new TerminationEvent();

        private TerminationEvent() {
        }

        @Override
        public void handleEvent(Handler handler) {
            handler.handleTerminated();
        }

        @Override
        public String toString() {
            return "TERMINATE";
        }
    }
}
