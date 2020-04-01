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
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

import static java.util.concurrent.TimeUnit.SECONDS
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.CREATED

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

    def "can stop and restart watching many directory many times"() {
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

        expect:
        20.times { iteration ->
            def watchedDirectories = createDirectoriesToWatch(numberOfWatchedDirectories, "iteration-$iteration/watchedDir-")

            def executorService = Executors.newFixedThreadPool(numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories)
            def readyLatch = new CountDownLatch(numberOfParallelWritersPerWatchedDirectory * numberOfWatchedDirectories)
            def startModifyingLatch = new CountDownLatch(1)
            def watcher = startNewWatcher(newEventSinkCallback())
            watchedDirectories.each { watchedDirectory ->
                numberOfParallelWritersPerWatchedDirectory.times { index ->
                    executorService.submit({ ->
                        def fileToChange = new File(watchedDirectory, "file${index}.txt")
                        readyLatch.countDown()
                        startModifyingLatch.await()
                        fileToChange.createNewFile()
                        500.times { modifyIndex ->
                            fileToChange << "Another change: $modifyIndex\n"
                        }
                    })
                }
            }
            executorService.shutdown()

            watcher.startWatching(watchedDirectories as File[])
            readyLatch.await()
            startModifyingLatch.countDown()
            Thread.sleep(500)
            watcher.close()

            assert executorService.awaitTermination(20, SECONDS)
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
        def watcher = startNewWatcher(newEventSinkCallback())
        watcher.startWatching(watchedDirectories as File[])
        waitForChangeEventLatency()
        assert rootDir.deleteDir()
        watcher.close()

        then:
        noExceptionThrown()
    }

    @Requires({ !Platform.current().linux })
    // TODO Fix overflow event on Windows
    @IgnoreIf({ Platform.current().windows })
    def "can stop watching a deep hierarchy when it has been deleted"() {
        given:
        def watchedDirectoryDepth = 10

        def watchedDir = new File(rootDir, "watchedDir")
        assert watchedDir.mkdir()
        createHierarchy(watchedDir, watchedDirectoryDepth)

        when:
        def watcher = startNewWatcher(newEventSinkCallback())
        watcher.startWatching(watchedDir)
        waitForChangeEventLatency()
        assert watchedDir.deleteDir()
        watcher.close()

        then:
        noExceptionThrown()
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
