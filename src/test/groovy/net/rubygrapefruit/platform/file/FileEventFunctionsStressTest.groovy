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

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Unroll

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static java.util.concurrent.TimeUnit.SECONDS
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class FileEventFunctionsStressTest extends AbstractFileEventFunctionsTest {

    def "can be started and stopped many times"() {
        when:
        100.times { i ->
            startWatcher(rootDir)
            stopWatcher()
        }

        then:
        noExceptionThrown()
    }

    def "can stop and restart watching directory many times"() {
        given:
        def createdFile = new File(rootDir, "created.txt")
        startWatcher(rootDir)
        100.times {
            assert watcher.stopWatching(rootDir)
            watcher.startWatching(rootDir)
        }

        when:
        createNewFile(createdFile)

        then:
        expectEvents change(CREATED, createdFile)
    }

    def "can stop and restart watching many directories many times"() {
        given:
        File[] watchedDirs = createDirectoriesToWatch(100)

        startWatcher()

        when:
        100.times { iteration ->
            watcher.startWatching(watchedDirs)
            assert watcher.stopWatching(watchedDirs)
        }

        then:
        noExceptionThrown()
    }

    @Timeout(value = 20, unit = SECONDS)
    @Unroll
    def "can start and stop watching directory while changes are being made to its contents, round #round"() {
        given:
        def numberOfParallelWritersPerWatchedDirectory = 10
        def numberOfWatchedDirectories = 10

        // We don't care about overflows here
        ignoreLogMessages()

        expect:
        def watchedDirectories = createDirectoriesToWatch(numberOfWatchedDirectories)

        def numberOfThreads = numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories
        def onslaught = new OnslaughtExecuter(numberOfThreads)
        def inTheMiddleLatch = new CountDownLatch(numberOfThreads)
        def watcher = startNewWatcher()
        def changeCount = new AtomicInteger()
        watchedDirectories.each { watchedDirectory ->
            numberOfParallelWritersPerWatchedDirectory.times { index ->
                def fileToChange = new File(watchedDirectory, "file${index}.txt")
                fileToChange.createNewFile()
                onslaught.submit({ ->
                    100.times { modifyIndex ->
                        fileToChange << "Change: $modifyIndex\n"
                        changeCount.incrementAndGet()
                    }
                    inTheMiddleLatch.countDown()
                    400.times { modifyIndex ->
                        fileToChange << "Another change: $modifyIndex\n"
                        changeCount.incrementAndGet()
                    }
                })
            }
        }

        watcher.startWatching(watchedDirectories)
        onslaught.start()

        inTheMiddleLatch.await()
        LOGGER.info("> Closing watcher (received ${eventQueue.size()} events of $changeCount changes)")
        watcher.close()
        LOGGER.info("< Closed watcher (received ${eventQueue.size()} events of $changeCount changes)")

        onslaught.terminate(20, SECONDS)
        LOGGER.info("< Finished test (received ${eventQueue.size()} events of $changeCount changes)")

        where:
        round << (1..20)
    }

    @Requires({ Platform.current().linux })
    def "can close watcher with many directories when they have been deleted"() {
        given:
        def watchedDirectoryDepth = 10

        def watchedDir = new File(rootDir, "watchedDir")
        assert watchedDir.mkdir()
        List<File> watchedDirectories = createHierarchy(watchedDir, watchedDirectoryDepth)

        when:
        def watcher = startNewWatcher()
        watcher.startWatching(watchedDirectories)
        waitForChangeEventLatency()
        assert rootDir.deleteDir()
        watcher.close()

        then:
        noExceptionThrown()
    }

    @Requires({ !Platform.current().linux })
    def "can stop watching a deep hierarchy when it has been deleted"() {
        given:
        def watchedDirectoryDepth = 10

        def watchedDir = new File(rootDir, "watchedDir")
        assert watchedDir.mkdir()
        createHierarchy(watchedDir, watchedDirectoryDepth)

        when:
        def watcher = startNewWatcher()
        watcher.startWatching(watchedDir)
        waitForChangeEventLatency()
        assert watchedDir.deleteDir()
        watcher.close()

        then:
        noExceptionThrown()
    }

    @Override
    protected TestFileWatcher startNewWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        // Make sure we don't receive overflow events during these tests
        return watcherFixture.startNewWatcherWithOverflowPrevention(eventQueue)
    }

    private static List<File> createHierarchy(File root, int watchedDirectoryDepth, int branching = 2) {
        List<File> allDirs = []
        allDirs.add(root)
        List<File> previousRoots = [root]
        (watchedDirectoryDepth - 1).times { depth ->
            previousRoots = previousRoots.collectMany { previousRoot ->
                List<File> dirs = []
                branching.times { index ->
                    def dir = new File(previousRoot, "dir${index}")
                    assert dir.mkdir()
                    dirs << dir
                }
                return dirs
            }
            allDirs.addAll(previousRoots)
        }
        LOGGER.info "> Created ${allDirs.size()} directories"
        return allDirs
    }

    private List<File> createDirectoriesToWatch(int numberOfWatchedDirectories) {
        (1..numberOfWatchedDirectories).collect {
            def dir = new File(rootDir, "dir-$it")
            assert dir.mkdirs()
            return dir
        }
    }

    private static class OnslaughtExecuter {
        private final ExecutorService executorService
        private final CountDownLatch readyLatch
        private final CountDownLatch startLatch
        private final int numberOfThreads
        private int submittedCount

        OnslaughtExecuter(int numberOfThreads) {
            this.numberOfThreads = numberOfThreads
            this.executorService = Executors.newFixedThreadPool(numberOfThreads)
            this.readyLatch = new CountDownLatch(numberOfThreads)
            this.startLatch = new CountDownLatch(1)
        }

        void submit(Runnable job) {
            submittedCount++
            executorService.submit({ ->
                readyLatch.countDown()
                startLatch.await()
                job.run()
            })
        }

        void start() {
            assert submittedCount == numberOfThreads
            readyLatch.await()
            LOGGER.info("> Starting onslaught on $numberOfThreads threads")
            startLatch.countDown()
        }

        void terminate(long timeout, TimeUnit unit) {
            executorService.shutdown()
            assert executorService.awaitTermination(timeout, unit)
        }
    }
}
