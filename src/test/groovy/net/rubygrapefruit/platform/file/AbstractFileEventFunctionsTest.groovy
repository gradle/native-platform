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

import groovy.transform.EqualsAndHashCode
import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions
import net.rubygrapefruit.platform.testfixture.JulLogging
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import static java.util.concurrent.TimeUnit.SECONDS
import static java.util.logging.Level.FINE

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
@Timeout(value = 10, unit = SECONDS)
abstract class AbstractFileEventFunctionsTest extends Specification {

    public static final Logger LOGGER = Logger.getLogger(AbstractFileEventFunctionsTest.name)

    @Rule
    TemporaryFolder tmpDir
    @Rule
    TestName testName
    @Rule
    JulLogging logging = new JulLogging(AbstractFileEventFunctions, FINE)

    def callback = new TestCallback()
    File testDir
    File rootDir
    FileWatcher watcher
    List<Throwable> uncaughtFailureOnThread

    // We could do this with @Delegate, but Groovy doesn't let us :(
    private FileWatcherFixture watcherFixture

    def setup() {
        watcherFixture = FileWatcherFixture.of(Platform.current())
        LOGGER.info(">>> Running '${testName.methodName}'")
        testDir = tmpDir.newFolder(testName.methodName).canonicalFile
        rootDir = new File(testDir, "root")
        assert rootDir.mkdirs()
        uncaughtFailureOnThread = []
    }

    def cleanup() {
        stopWatcher()
        uncaughtFailureOnThread.each {
            it.printStackTrace()
        }
        assert uncaughtFailureOnThread.empty
        LOGGER.info("<<< Finished '${testName.methodName}'")
    }

    enum FileWatcherFixture {
        MAC_OS(){
            private static final int LATENCY_IN_MILLIS = 0

            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                Native.get(OsxFileEventFunctions.class).startWatcher(
                    LATENCY_IN_MILLIS, TimeUnit.MILLISECONDS,
                    callback
                )
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(LATENCY_IN_MILLIS + 20)
            }
        },
        LINUX(){
            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                Native.get(LinuxFileEventFunctions.class).startWatcher(callback)
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(50)
            }
        },
        WINDOWS(){
            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                Native.get(WindowsFileEventFunctions.class).startWatcher(callback)
            }

            @Override
            void waitForChangeEventLatency() {
                Thread.sleep(50)
            }
        },
        UNSUPPORTED() {
            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
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

        abstract FileWatcher startNewWatcher(FileWatcherCallback callback)

        abstract void waitForChangeEventLatency()
    }

    protected static class TestCallback implements FileWatcherCallback {
        private AsyncConditions conditions
        private Collection<FileEvent> expectedEvents = []

        AsyncConditions expect(List<FileEvent> events) {
            events.each { event ->
                LOGGER.info("> Expecting $event")
            }
            this.conditions = new AsyncConditions()
            this.expectedEvents = new ArrayList<>(events)
            return conditions
        }

        @Override
        void pathChanged(Type type, String path) {
            def changed = new File(path)
            if (!changed.absolute) {
                throw new IllegalArgumentException("Received non-absolute changed path: " + path)
            }
            handleEvent(new FileEvent(type, changed, true))
        }

        @Override
        void reportError(Throwable ex) {
            System.err.print("Error reported from native backend:")
            ex.printStackTrace()
            uncaughtFailureOnThread << ex
        }

        private void handleEvent(FileEvent event) {
            LOGGER.info("> Received  $event")
            if (!expectedEvents.remove(event)) {
                conditions.evaluate {
                    throw new RuntimeException("Unexpected event $event")
                }
            }
            if (!expectedEvents.any { it.mandatory }) {
                conditions.evaluate {}
            }
        }
    }

    @EqualsAndHashCode(excludes = ["mandatory"])
    @SuppressWarnings("unused")
    protected static class FileEvent {
        final FileWatcherCallback.Type type
        final File file
        final boolean mandatory

        FileEvent(FileWatcherCallback.Type type, File file, boolean mandatory) {
            this.type = type
            this.file = file
            this.mandatory = mandatory
        }

        @Override
        String toString() {
            return "${mandatory ? "" : "optional "}$type $file"
        }
    }

    protected FileWatcher startNewWatcher(FileWatcherCallback callback) {
        watcherFixture.startNewWatcher(callback)
    }

    protected void waitForChangeEventLatency() {
        watcherFixture.waitForChangeEventLatency()
    }

    protected void startWatcher(FileWatcherCallback callback = this.callback, File... roots) {
        watcher = startNewWatcher(callback)
        roots*.absoluteFile.each { root ->
            watcher.startWatching(root)
        }
    }

    protected void stopWatcher() {
        def copyWatcher = watcher
        watcher = null
        copyWatcher?.close()
    }

    protected AsyncConditions expectNoEvents(FileWatcherCallback callback = this.callback) {
        expectEvents(callback, [])
    }

    protected AsyncConditions expectEvents(FileWatcherCallback callback = this.callback, FileEvent... events) {
        expectEvents(callback, events as List)
    }

    protected AsyncConditions expectEvents(FileWatcherCallback callback = this.callback, List<FileEvent> events) {
        return callback.expect(events)
    }

    protected static FileEvent event(FileWatcherCallback.Type type, File file, boolean mandatory = true) {
        return new FileEvent(type, file, mandatory)
    }

    protected static void createNewFile(File file) {
        LOGGER.info("> Creating $file")
        file.createNewFile()
        LOGGER.info("< Created $file")
    }
}
