package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatchEvent.OverflowType;
import net.rubygrapefruit.platform.file.FileWatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractFileEventFunctions<W extends FileWatcher> implements NativeIntegration {
    public abstract static class AbstractWatcherBuilder<T extends FileWatcher> {
        public static final long DEFAULT_START_TIMEOUT_IN_SECONDS = 5;

        private final BlockingQueue<FileWatchEvent> eventQueue;

        public AbstractWatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            this.eventQueue = eventQueue;
        }

        /**
         * Start the file watcher.
         *
         * @throws FileWatcherTimeoutException if the watcher did not start up
         * in {@value DEFAULT_START_TIMEOUT_IN_SECONDS} seconds.
         * @throws InterruptedException if the current thread has been interrupted.
         *
         * @see FileWatcher#startWatching(Collection)
         */
        public T start() throws InterruptedException {
            return start(DEFAULT_START_TIMEOUT_IN_SECONDS, SECONDS);
        }

        /**
         * Start the file watcher with the given timeout.
         *
         * @throws FileWatcherTimeoutException if the watcher did not start up in
         * the given timeout.
         * @throws InterruptedException if the current thread has been interrupted.
         *
         * @see FileWatcher#startWatching(Collection)
         */
        public T start(long startTimeout, TimeUnit startTimeoutUnit) throws InterruptedException, InsufficientResourcesForWatchingException {
            NativeFileWatcherCallback callback = new NativeFileWatcherCallback(eventQueue);
            T watcher = createWatcher(callback);
            watcher.initialize(startTimeout, startTimeoutUnit);
            return watcher;
        }

        protected abstract T createWatcher(NativeFileWatcherCallback callback);
    }

    /**
     * Configures a new watcher using a builder.
     * Call {@link AbstractWatcherBuilder#start()} to actually start the {@link FileWatcher}.
     *
     * The queue must have a total capacity of at least 2 elements.
     * The caller should only consume events from the queue, and never add any of their own.
     */
    public abstract AbstractWatcherBuilder<W> newWatcher(BlockingQueue<FileWatchEvent> queue);

    protected static class NativeFileWatcherCallback {

        private final BlockingQueue<FileWatchEvent> eventQueue;

        public NativeFileWatcherCallback(BlockingQueue<FileWatchEvent> eventQueue) {
            this.eventQueue = eventQueue;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportChangeEvent(int typeIndex, String path) {
            FileWatchEvent.ChangeType type = FileWatchEvent.ChangeType.values()[typeIndex];
            queueEvent(new ChangeEvent(type, path), false);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportUnknownEvent(String path) {
            queueEvent(new UnknownEvent(path), false);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportOverflow(@Nullable String path) {
            signalOverflow(OverflowType.OPERATING_SYSTEM, path);
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void reportFailure(Throwable ex) {
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

    protected static abstract class AbstractFileWatcher implements FileWatcher {
        private final CountDownLatch runLoopInitialized = new CountDownLatch(1);
        private final Thread processorThread;
        private boolean shutdown;

        public AbstractFileWatcher(final NativeFileWatcherCallback callback) {
            this.processorThread = new Thread("File watcher server") {
                @Override
                public void run() {
                    initializeRunLoop();
                    runLoopInitialized.countDown();
                    try {
                        executeRunLoop();
                        if (!shutdown) {
                            callback.reportFailure(new FileWatcherException("File watcher server did exit without being shutdown"));
                        }
                    } catch (Throwable e) {
                        callback.reportFailure(e);
                    } finally {
                        callback.reportTermination();
                    }
                }
            };
            this.processorThread.setDaemon(true);
        }

        @Override
        public void initialize(long startTimeout, TimeUnit startTimeoutUnit) throws InterruptedException {
            this.processorThread.start();
            boolean started = runLoopInitialized.await(startTimeout, startTimeoutUnit);
            if (!started) {
                // Note: we don't close here because we have no idea what state the native backend is in
                throw new FileWatcherTimeoutException("Starting the watcher timed out");
            }
        }

        @Override
        public void startWatching(Collection<File> paths) {
            ensureOpen();
            doStartWatching(paths);
        }

        @Override
        public boolean stopWatching(Collection<File> paths) {
            ensureOpen();
            return doStopWatching(paths);
        }

        @Override
        public void shutdown() {
            ensureOpen();
            shutdown = true;
            doShutdown();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            long timeoutInMillis = unit.toMillis(timeout);
            long startTime = System.currentTimeMillis();
            boolean successful = awaitTermination(timeoutInMillis);
            if (successful) {
                long endTime = System.currentTimeMillis();
                long remainingTimeout = timeoutInMillis - (endTime - startTime);
                if (remainingTimeout > 0) {
                    processorThread.join(remainingTimeout);
                }
                return !processorThread.isAlive();
            } else {
                return false;
            }
        }

        protected abstract void initializeRunLoop();

        protected abstract void executeRunLoop();

        protected abstract void doStartWatching(Collection<File> paths);

        protected abstract boolean doStopWatching(Collection<File> paths);

        protected abstract void doShutdown();

        protected abstract boolean awaitTermination(long timeoutInMillis);

        private void ensureOpen() {
            if (shutdown) {
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
        public final static TerminationEvent INSTANCE = new TerminationEvent();

        private TerminationEvent() {}

        @Override
        public void handleEvent(Handler handler) {
            handler.handleTerminated();
        }

        @Override
        public String toString() {
            return "TERMINATE";
        }
    }

    public static class FileWatcherException extends NativeException {
        public FileWatcherException(String message) {
            super(message);
        }
    }

    public static class FileWatcherTimeoutException extends FileWatcherException {
        public FileWatcherTimeoutException(String message) {
            super(message);
        }
    }
}
