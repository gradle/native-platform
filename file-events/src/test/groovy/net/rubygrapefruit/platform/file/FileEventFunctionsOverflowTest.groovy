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
import org.spockframework.util.Nullable
import spock.lang.Ignore
import spock.lang.Requires

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.SECONDS
import static java.util.logging.Level.INFO
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class FileEventFunctionsOverflowTest extends AbstractFileEventFunctionsTest {

    @Ignore("Flaky")
    def "delivers more events after overflow event"() {
        given:
        // We don't want to fail when overflow is logged
        ignoreLogMessages()

        def afterOverflowFile = new File(rootDir, "after-overflow.txt")

        def numberOfParallelWriters = 10
        def executorService = Executors.newFixedThreadPool(numberOfParallelWriters)
        def readyLatch = new CountDownLatch(numberOfParallelWriters)
        def finishedLatch = new CountDownLatch(numberOfParallelWriters)
        def startModifyingLatch = new CountDownLatch(1)
        numberOfParallelWriters.times { index ->
            executorService.submit({ ->
                def fileToChange = new File(rootDir, "file-${index}")
                readyLatch.countDown()
                startModifyingLatch.await()
                2000.times {
                    fileToChange.delete()
                    fileToChange.createNewFile()
                }
                finishedLatch.countDown()
            })
        }

        when:
        startWatcher(rootDir)
        readyLatch.await()
        startModifyingLatch.countDown()
        finishedLatch.await(5, SECONDS)
        waitForChangeEventLatency()

        then:
        expectOverflow(5, SECONDS)

        when:
        LOGGER.info("> Making change after overflow")
        afterOverflowFile.createNewFile()

        then:
        expectEvents change(CREATED, afterOverflowFile)

        cleanup:
        executorService.shutdown()
    }

    def "handles Java-side event queue overflowing"() {
        given:
        def singleElementQueue = new ArrayBlockingQueue<FileWatchEvent>(1)
        def firstFile = new File(rootDir, "first.txt")
        def secondFile = new File(rootDir, "second.txt")

        startWatcher(singleElementQueue, rootDir)

        when:
        createNewFile(firstFile)
        waitForChangeEventLatency()

        then:
        singleElementQueue.peek().handleEvent(new AbstractFileEventFunctionsTest.TestHandler() {
            @Override
            void handleChangeEvent(FileWatchEvent.ChangeType type, String absolutePath) {
                assert type == CREATED
                assert absolutePath == firstFile.absolutePath
            }
        })

        when:
        createNewFile(secondFile)
        waitForChangeEventLatency()

        then:
        singleElementQueue.poll().handleEvent(new AbstractFileEventFunctionsTest.TestHandler() {
            @Override
            void handleOverflow(FileWatchEvent.OverflowType type, @Nullable String absolutePath) {
                // Good
            }
        })

        then:
        singleElementQueue.empty

        expectLogMessage(INFO, "Event queue overflow, dropping all events")
    }

    private boolean expectOverflow(BlockingQueue<FileWatchEvent> eventQueue = this.eventQueue, int timeoutValue, TimeUnit timeoutUnit) {
        boolean overflow = false
        expectEvents(eventQueue, timeoutValue, timeoutUnit, { -> true }, { event ->
            if (event == null) {
                return false
            } else {
                event.handleEvent(new AbstractFileEventFunctionsTest.TestHandler() {
                    @Override
                    void handleOverflow(FileWatchEvent.OverflowType type, @Nullable String absolutePath) {
                        overflow = true
                    }
                })
            }
            return true
        })
        return overflow
    }
}
