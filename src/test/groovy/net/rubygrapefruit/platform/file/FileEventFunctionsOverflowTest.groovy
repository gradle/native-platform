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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.CREATED
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.INVALIDATE

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class FileEventFunctionsOverflowTest extends AbstractFileEventFunctionsTest {

    def "delivers more events after overflow event"() {
        given:
        // We don't want to fail when overflow is logged
        ignoreLogMessages()

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
                100.times {
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

        then:
        expectOverflow()

        when:
        // Finish receiving events
        finishedLatch.await()
        waitForChangeEventLatency()
        eventQueue.clear()

        def afterOverflowFile = new File(rootDir, "after-overflow.txt")
        afterOverflowFile.createNewFile()

        then:
        expectEvents change(CREATED, afterOverflowFile)

        cleanup:
        executorService.shutdown()
    }

    private boolean expectOverflow() {
        boolean overflow = false
        expectEvents { event ->
            if (event == null) {
                return false
            } else if (event.type == INVALIDATE) {
                overflow = true
            }
            return true
        }
        return overflow
    }
}
