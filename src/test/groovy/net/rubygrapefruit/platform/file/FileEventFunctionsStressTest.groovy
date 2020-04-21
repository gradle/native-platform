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

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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

    @Timeout(value = 180, unit = SECONDS)
    def "can start and stop watching directory while changes are being made to its contents"() {
        given:
        def numberOfParallelWritersPerWatchedDirectory = 10
        def numberOfWatchedDirectories = 10

        // We don't care about overflows here
        ignoreLogMessages()

        expect:
        20.times { iteration ->
            LOGGER.info(">> Round #${iteration + 1}")
            def watchedDirectories = createDirectoriesToWatch(numberOfWatchedDirectories, "iteration-$iteration/watchedDir-")

            def numberOfThreads = numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories
            def executorService = Executors.newFixedThreadPool(numberOfThreads)
            def readyLatch = new CountDownLatch(numberOfThreads)
            def startModifyingLatch = new CountDownLatch(1)
            def inTheMiddleLatch = new CountDownLatch(numberOfThreads)
            def eventQueue = newEventQueue()
            def watcher = startNewWatcher(eventQueue)
            def changeCount = new AtomicInteger()
            watchedDirectories.each { watchedDirectory ->
                numberOfParallelWritersPerWatchedDirectory.times { index ->
                    executorService.submit({ ->
                        def fileToChange = new File(watchedDirectory, "file${index}.txt")
                        readyLatch.countDown()
                        startModifyingLatch.await()
                        fileToChange.createNewFile()
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
            executorService.shutdown()

            watcher.startWatching(watchedDirectories as File[])
            readyLatch.await()
            LOGGER.info("> Starting changes on $numberOfThreads threads")
            startModifyingLatch.countDown()
            inTheMiddleLatch.await()
            LOGGER.info("> Closing watcher (received ${eventQueue.size()} events of $changeCount changes)")
            watcher.close()
            LOGGER.info("< Closed watcher (received ${eventQueue.size()} events of $changeCount changes)")

            assert executorService.awaitTermination(20, SECONDS)
            LOGGER.info("< Finished test (received ${eventQueue.size()} events of $changeCount changes)")

            // Let's make sure we free up memory as much as we can
            eventQueue.clear()

            assert uncaughtFailureOnThread.empty
        }
    }

    @Requires({ Platform.current().linux })
    def "can stop watching many directories when they have been deleted"() {
        given:
        def watchedDirectoryDepth = 10

        def watchedDir = new File(rootDir, "watchedDir")
        assert watchedDir.mkdir()
        List<File> watchedDirectories = createHierarchy(watchedDir, watchedDirectoryDepth)

        when:
        def watcher = startNewWatcher()
        watcher.startWatching(watchedDirectories as File[])
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

    private List<File> createDirectoriesToWatch(int numberOfWatchedDirectories, String prefix = "dir-") {
        (1..numberOfWatchedDirectories).collect {
            def dir = new File(rootDir, prefix + it)
            assert dir.mkdirs()
            return dir
        }
    }
}
