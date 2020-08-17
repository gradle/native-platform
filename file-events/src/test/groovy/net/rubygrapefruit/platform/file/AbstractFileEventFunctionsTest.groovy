/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.rubygrapefruit.platform.file

import groovy.transform.Memoized
import net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType
import net.rubygrapefruit.platform.file.FileWatchEvent.OverflowType
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.NativeLogger
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions
import net.rubygrapefruit.platform.testfixture.JniChecksEnabled
import net.rubygrapefruit.platform.testfixture.JulLogging
import org.junit.Rule
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import org.spockframework.util.Assert
import org.spockframework.util.Nullable
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

import static java.util.concurrent.TimeUnit.SECONDS
import static java.util.logging.Level.CONFIG
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.EventStatus.EXPECTED
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.EventStatus.UNEXPECTED

@Timeout(value = 10, unit = SECONDS)
@Category(JniChecksEnabled)
abstract class AbstractFileEventFunctionsTest extends Specification {

    public static final Logger LOGGER = Logger.getLogger(AbstractFileEventFunctionsTest.name)

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder(tmpDir())
    @Rule
    TestName testName
    @Rule
    JulLogging logging = new JulLogging(NativeLogger, CONFIG)

    def eventQueue = newEventQueue()
    File testDir
    File rootDir
    TestFileWatcher watcher
    List<Throwable> uncaughtFailureOnThread

    private Map<Pattern, Level> expectedLogMessages

    // We could do this with @Delegate, but Groovy doesn't let us :(
    protected FileWatcherFixture watcherFixture

    private static File tmpDir() {
        File tmpFile = new File(System.getProperty("test.directory")).absoluteFile
        if (!tmpFile.directory) {
            assert tmpFile.mkdirs()
        }
        return tmpFile
    }

    def setup() {
        watcherFixture = FileWatcherFixture.of(Platform.current())
        LOGGER.info(">>> Running '${testName.methodName}'")
        testDir = tmpDir.newFolder(testName.methodName).canonicalFile
        rootDir = new File(testDir, "root")
        assert rootDir.mkdirs()
        uncaughtFailureOnThread = []
        expectedLogMessages = [:]
    }

    def cleanup() {
        shutdownWatcher()
        LOGGER.info("<<< Finished '${testName.methodName}'")

        uncaughtFailureOnThread.each {
            it.printStackTrace()
        }
        // Avoid power assertion printing exceptions again
        Assert.that(uncaughtFailureOnThread.empty, "There were uncaught exceptions, see stacktraces above")

        // Check if the logs (INFO and above) match our expectations
        if (expectedLogMessages != null) {
            Map<String, Level> unexpectedLogMessages = logging.messages
                .findAll { message, level -> level.intValue() >= Level.INFO.intValue() }
            def remainingExpectedLogMessages = new LinkedHashMap<Pattern, Level>(expectedLogMessages)
            unexpectedLogMessages.removeAll { message, level ->
                remainingExpectedLogMessages.removeAll { expectedMessage, expectedLevel ->
                    expectedMessage.matcher(message).matches() && expectedLevel == level
                }
            }
            Assert.that(
                unexpectedLogMessages.isEmpty() && remainingExpectedLogMessages.isEmpty(),
                createLogMessageFailure(unexpectedLogMessages, remainingExpectedLogMessages)
            )
        }
    }

    private static String createLogMessageFailure(Map<String, Level> unexpectedLogMessages, LinkedHashMap<Pattern, Level> remainingExpectedLogMessages) {
        String failure = "Log messages differ from expected:\n"
        unexpectedLogMessages.each { message, level ->
            failure += " - UNEXPECTED $level $message\n"
        }
        remainingExpectedLogMessages.each { message, level ->
            failure += " - MISSING    $level $message\n"
        }
        return failure
    }

    void ignoreLogMessages() {
        expectedLogMessages = null
    }

    void expectLogMessage(Level level, String message) {
        expectLogMessage(level, Pattern.compile(Pattern.quote(message)))
    }

    void expectLogMessage(Level level, Pattern pattern) {
        expectedLogMessages.put(pattern, level)
    }

    enum FileWatcherFixture {
        MAC_OS(){
            private static final int LATENCY_IN_MILLIS = 0

            @Memoized
            @Override
            OsxFileEventFunctions getService() {
                FileEvents.get(OsxFileEventFunctions)
            }

            @Override
            FileWatcher startNewWatcherInternal(BlockingQueue<FileWatchEvent> eventQueue, boolean preventOverflow) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                service.newWatcher(eventQueue)
                    .withLatency(LATENCY_IN_MILLIS, TimeUnit.MILLISECONDS)
                    .start()
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(LATENCY_IN_MILLIS + 50)
            }
        },
        LINUX(){
            @Memoized
            @Override
            LinuxFileEventFunctions getService() {
                FileEvents.get(LinuxFileEventFunctions)
            }

            @Override
            FileWatcher startNewWatcherInternal(BlockingQueue<FileWatchEvent> eventQueue, boolean preventOverflow) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                service.newWatcher(eventQueue)
                    .start()
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(50)
            }
        },
        WINDOWS(){
            @Memoized
            @Override
            WindowsFileEventFunctions getService() {
                FileEvents.get(WindowsFileEventFunctions)
            }

            @Override
            FileWatcher startNewWatcherInternal(BlockingQueue<FileWatchEvent> eventQueue, boolean preventOverflow) {
                int bufferSizeInKb
                if (preventOverflow) {
                    bufferSizeInKb = 16384
                    AbstractFileEventFunctionsTest.LOGGER.info("Using $bufferSizeInKb kByte buffer to prevent overflow events");
                } else {
                    bufferSizeInKb = 16
                }
                service.newWatcher(eventQueue)
                    .withBufferSize(bufferSizeInKb * 1024)
                    .start()
            }

            @Override
            void waitForChangeEventLatency() {
                Thread.sleep(50)
            }
        },
        UNSUPPORTED() {
            @Override
            AbstractFileEventFunctions getService() {
                throw new UnsupportedOperationException()
            }

            @Override
            FileWatcher startNewWatcherInternal(BlockingQueue<FileWatchEvent> eventQueue, boolean preventOverflow) {
                throw new UnsupportedOperationException()
            }

            @Override
            void waitForChangeEventLatency() {
                throw new UnsupportedOperationException()
            }
        }

        static FileWatcherFixture of(Platform platform) {
            if (platform.macOs) {
                return MAC_OS
            } else if (platform.linux) {
                return LINUX
            } else if (platform.windows) {
                return WINDOWS
            } else {
                return UNSUPPORTED
            }
        }

        abstract AbstractFileEventFunctions getService()

        abstract FileWatcher startNewWatcherInternal(BlockingQueue<FileWatchEvent> eventQueue, boolean preventOverflow)

        TestFileWatcher startNewWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
            new TestFileWatcher(startNewWatcherInternal(eventQueue, false))
        }

        /**
         * Create a watcher that has a larger buffer to avoid overflow events happening during stress tests.
         * Overflow events are okay when we have lots of chagnes, but they make it impossible to test
         * other behavior we care about in stress tests.
         */
        TestFileWatcher startNewWatcherWithOverflowPrevention(BlockingQueue<FileWatchEvent> eventQueue) {
            new TestFileWatcher(startNewWatcherInternal(eventQueue, true))
        }

        abstract void waitForChangeEventLatency()
    }

    protected static BlockingQueue<FileWatchEvent> newEventQueue() {
        new LinkedBlockingQueue<FileWatchEvent>()
    }

    private enum EventStatus {
        EXPECTED, UNEXPECTED
    }

    private interface ExpectedEvent {
        boolean matches(FileWatchEvent event)
        boolean isOptional()
    }

    private class ExpectedChange implements ExpectedEvent {
        private final ChangeType type
        private final File file
        final boolean optional

        ExpectedChange(ChangeType type, File file, boolean optional) {
            this.type = type
            this.file = file
            this.optional = optional
        }

        @Override
        boolean matches(FileWatchEvent event) {
            def matcher = new MatcherHandler() {
                @Override
                void handleChangeEvent(ChangeType type, String absolutePath) {
                    matched = ExpectedChange.this.type == type && ExpectedChange.this.file.absolutePath == absolutePath
                }
            }
            event.handleEvent(matcher)
            return matcher.matched
        }

        @Override
        String toString() {
            return "${optional ? "optional " : ""}$type ${file == null ? null : shorten(file)}"
        }
    }

    private class ExpectedFailure implements ExpectedEvent {
        private final Pattern message
        private final Class<? extends Throwable> type

        ExpectedFailure(Class<? extends Throwable> type, Pattern message) {
            this.type = type
            this.message = message
        }

        @Override
        boolean matches(FileWatchEvent event) {
            def matcher = new MatcherHandler() {
                @Override
                void handleFailure(Throwable failure) {
                    matched = type.isInstance(failure) && message.matcher(failure.message).matches()
                }
            }
            event.handleEvent(matcher)
            return matcher.matched
        }

        @Override
        boolean isOptional() {
            false
        }

        @Override
        String toString() {
            return "FAILURE /${message.pattern()}/"
        }
    }

    private class ExpectedTermination implements ExpectedEvent {
        @Override
        boolean matches(FileWatchEvent event) {
            def matcher = new MatcherHandler() {
                @Override
                void handleTerminated() {
                    matched = true
                }
            }
            event.handleEvent(matcher)
            return matcher.matched
        }

        @Override
        boolean isOptional() {
            return false
        }

        @Override
        String toString() {
            return "TERMINATE"
        }
    }

    protected AbstractFileEventFunctions getService() {
        watcherFixture.service
    }

    protected void waitForChangeEventLatency() {
        watcherFixture.waitForChangeEventLatency()
    }

    protected void startWatcher(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue, File... roots) {
        watcher = startNewWatcher(eventQueue, roots)
    }

    protected TestFileWatcher startNewWatcher(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue, File... roots) {
        def watcher = startNewWatcher(eventQueue)
        watcher.startWatching(roots)
        return watcher
    }

    protected TestFileWatcher startNewWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        watcherFixture.startNewWatcher(eventQueue)
    }

    protected void shutdownWatcher() {
        def copyWatcher = watcher
        watcher = null
        if (copyWatcher != null) {
            shutdownWatcher(copyWatcher)
        }
    }

    protected static void shutdownWatcher(FileWatcher watcher) {
        watcher.shutdown()
        assert watcher.awaitTermination(5, SECONDS)
    }

    protected static <T> T byPlatform(Map<PlatformType, T> platforms) {
        T match = platforms.find { platform, _ -> platform.matches(Platform.current()) }?.value
        Assert.that(match != null, "No match for platform ${Platform.current()}")
        return match
    }

    private void ensureNoMoreEvents(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue) {
        def receivedEvents = new ArrayList<FileWatchEvent>()
        eventQueue.drainTo(receivedEvents)
        Assert.that(
            receivedEvents.empty,
            createEventFailure(receivedEvents.collectEntries { event -> [event, UNEXPECTED]}, [])
        )
    }

    protected void expectNoEvents(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue) {
        // Let's make sure there are no events occurring,
        // and we don't just miss them because of timing
        waitForChangeEventLatency()
        ensureNoMoreEvents(eventQueue)
    }

    protected void expectEvents(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue, int timeoutValue = 1, TimeUnit timeoutUnit = SECONDS, ExpectedEvent... events) {
        expectEvents(eventQueue, timeoutValue, timeoutUnit, events as List)
    }

    protected void expectEvents(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue, int timeoutValue = 1, TimeUnit timeoutUnit = SECONDS, List<ExpectedEvent> expectedEvents) {
        expectedEvents.each { expectedEvent ->
            LOGGER.info("> Expecting $expectedEvent")
        }
        def remainingExpectedEvents = new ArrayList<ExpectedEvent>(expectedEvents)
        Map<FileWatchEvent, EventStatus> receivedEvents = [:]
        expectEvents(
            eventQueue,
            timeoutValue,
            timeoutUnit,
            { !remainingExpectedEvents.empty },
            { event ->
                if (event == null) {
                    return false
                }
                LOGGER.info("> Received $event")
                def expectedEventIndex = remainingExpectedEvents.findIndexOf { expectedEvent ->
                    expectedEvent.matches(event)
                }
                EventStatus status
                if (expectedEventIndex == -1) {
                    status = UNEXPECTED
                } else {
                    remainingExpectedEvents.remove(expectedEventIndex)
                    status = EXPECTED
                }
                receivedEvents.put(event, status)
                return true
            })
        Assert.that(
            remainingExpectedEvents.every { it.optional } && receivedEvents.values().every { it == EXPECTED },
            createEventFailure(receivedEvents, remainingExpectedEvents)
        )
        ensureNoMoreEvents(eventQueue)
    }

    private String createEventFailure(Map<FileWatchEvent, EventStatus> receivedEvents, List<ExpectedEvent> remainingExpectedEvents) {
        String failure = "Events received differ from expected:\n"
        receivedEvents.each { event, status ->
            failure += " - ${status == EXPECTED ? "RECEIVED  " : "UNEXPECTED"} ${format(event)}\n"
        }
        remainingExpectedEvents.each { event ->
            failure += " - MISSING    $event\n"
        }
        return failure
    }

    protected void expectEvents(
        BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue,
        int timeoutValue = 1,
        TimeUnit timeoutUnit = SECONDS,
        BooleanSupplier shouldContinue = { true },
        Predicate<FileWatchEvent> eventHandler
    ) {
        long start = System.currentTimeMillis()
        long end = start + timeoutUnit.toMillis(timeoutValue)
        while (shouldContinue.asBoolean) {
            def current = System.currentTimeMillis()
            long timeout = end - current
            def event = eventQueue.poll(timeout, TimeUnit.MILLISECONDS)
            if (!eventHandler.test(event)) {
                break
            }
        }
    }

    protected String format(FileWatchEvent event) {
        String shortened = null
        event.handleEvent(new FileWatchEvent.Handler() {
            @Override
            void handleChangeEvent(ChangeType type, String absolutePath) {
                shortened = type.name() + " " + shorten(absolutePath)
            }

            @Override
            void handleUnknownEvent(@Nullable String absolutePath) {
                shortened = "UNKNOWN ${shorten(absolutePath)}"
            }

            @Override
            void handleOverflow(OverflowType type, @Nullable String absolutePath) {
                shortened = "OVERFLOW ${shorten(absolutePath)} ($type)"
            }

            @Override
            void handleFailure(Throwable failure) {
                shortened = "FAILURE $failure"
            }

            @Override
            void handleTerminated() {
                shortened = "TERMINATE"
            }
        })
        assert shortened != null
        return shortened
    }

    protected String shorten(File file) {
        shorten(file.absolutePath)
    }

    protected String shorten(@Nullable String path) {
        if (path == null) {
            return "null"
        }
        def prefix = testDir.absolutePath
        return path.startsWith(prefix + File.separator)
            ? "..." + path.substring(prefix.length())
            : path
    }

    protected ExpectedEvent change(ChangeType type, File file) {
        new ExpectedChange(type, file, false)
    }

    protected ExpectedEvent optionalChange(ChangeType type, File file) {
        return new ExpectedChange(type, file, true)
    }

    protected ExpectedEvent failure(Class<? extends Throwable> type = Exception, String message) {
        failure(type, Pattern.quote(message))
    }

    protected ExpectedEvent failure(Class<? extends Throwable> type = Exception, Pattern message) {
        return new ExpectedFailure(type, message)
    }

    protected ExpectedEvent termination() {
        return new ExpectedTermination()
    }

    protected void createNewFile(File file) {
        LOGGER.info("> Creating ${shorten(file)}")
        file.createNewFile()
        LOGGER.info("< Created ${shorten(file)}")
    }

    static class TestFileWatcher implements FileWatcher {
        @Delegate
        private final FileWatcher delegate

        TestFileWatcher(FileWatcher delegate) {
            this.delegate = delegate
        }

        void startWatching(File... paths) {
            delegate.startWatching(paths as List)
        }

        boolean stopWatching(File... paths) {
            delegate.stopWatching(paths as List)
        }
    }

    private static class MatcherHandler implements FileWatchEvent.Handler {
        boolean matched

        @Override
        void handleChangeEvent(ChangeType type, String absolutePath) {}

        @Override
        void handleOverflow(OverflowType type, @Nullable String absolutePath) {}

        @Override
        void handleUnknownEvent(@Nullable String absolutePath) {}

        @Override
        void handleFailure(Throwable failure) {}

        @Override
        void handleTerminated() {}
    }

    protected static class TestHandler implements FileWatchEvent.Handler {
        @Override
        void handleChangeEvent(ChangeType type, String absolutePath) {
            throw new IllegalStateException(String.format("Received unexpected change with %s / %s", type, absolutePath))
        }

        @Override
        void handleOverflow(OverflowType type, @Nullable String absolutePath) {
            throw new IllegalStateException(String.format("Received unexpected %s overflow at %s", type, absolutePath))
        }

        @Override
        void handleUnknownEvent(@Nullable String absolutePath) {
            throw new IllegalStateException(String.format("Received unexpected unknown event at %s", absolutePath))
        }

        @Override
        void handleFailure(Throwable failure) {
            throw new IllegalStateException(String.format("Received unexpected failure", failure))
        }

        @Override
        void handleTerminated() {
            throw new IllegalStateException("Received unexpected termination")
        }
    }

    protected static enum PlatformType {
        LINUX() {
            @Override
            boolean matches(Platform platform) {
                return platform.linux
            }
        },
        MAC_OS() {
            @Override
            boolean matches(Platform platform) {
                return platform.macOs
            }
        },
        WINDOWS() {
            @Override
            boolean matches(Platform platform) {
                return platform.windows
            }
        },
        OTHERWISE() {
            @Override
            boolean matches(Platform platform) {
                return true
            }
        };

        abstract boolean matches(Platform platform)
    }
}
