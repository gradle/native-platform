/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions
import spock.lang.Requires

import java.util.concurrent.TimeUnit

import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.MODIFIED

@Requires({ Platform.current().macOs })
class OsxFileEventFunctionsTest extends AbstractFileEventsTest {
    private static final LATENCY_IN_MILLIS = 0

    final OsxFileEventFunctions fileEvents = Native.get(OsxFileEventFunctions.class)

    @Override
    protected FileWatcher startNewWatcher(FileWatcherCallback callback) {
        // Avoid setup operations to be reported
        waitForChangeEventLatency()
        fileEvents.startWatcher(
            LATENCY_IN_MILLIS, TimeUnit.MILLISECONDS,
            callback
        )
    }

    @Override
    protected void waitForChangeEventLatency() {
        TimeUnit.MILLISECONDS.sleep(LATENCY_IN_MILLIS + 20)
    }

    // This is a macOS-specific behavior
    def "changing metadata immediately after creation is reported as modified"() {
        given:
        def createdFile = new File(rootDir, "file.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(MODIFIED, createdFile)
        createNewFile(createdFile)
        createdFile.setReadable(false)

        then:
        expectedChanges.await()
    }

    def "can watch symlinked directory twice"() {
        given:
        def canonicalDir = new File(rootDir, "watchedDir")
        def canonicalFile = new File(canonicalDir, "modified.txt")
        canonicalDir.mkdirs()
        createNewFile(canonicalFile)
        def linkedDir = new File(rootDir, "linked")
        def watchedFile = new File(linkedDir, "modified.txt")
        java.nio.file.Files.createSymbolicLink(linkedDir.toPath(), canonicalDir.toPath())
        startWatcher(canonicalDir, linkedDir)

        when:
        def expectedChanges = expectEvents Platform.current().macOs
            ? event(MODIFIED, canonicalFile)
            : event(MODIFIED, watchedFile)
        watchedFile << "change"

        then:
        expectedChanges.await()
    }
}
