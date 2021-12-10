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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.NativeLogger
import org.junit.Assume
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Unroll

import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

import static java.util.concurrent.TimeUnit.SECONDS
import static java.util.logging.Level.INFO
import static java.util.logging.Level.SEVERE
import static java.util.logging.Level.WARNING
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.OTHERWISE
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.WINDOWS
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.INVALIDATED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.MODIFIED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.REMOVED

@Unroll
@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class MovedDirectoriesFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    def "can detect #ancestry removed"() {
        given:
        def parentDir = new File(rootDir, "parent")
        def watchedDir = new File(parentDir, "removed")
        watchedDir.mkdirs()
        def removedFile = new File(watchedDir, "file.txt")
        createNewFile(removedFile)
        File removedDir = removedDirectory(watchedDir)
        startWatcher(watchedDir)

        when:
        def directoryRemoved = removedDir.deleteDir()
        // On Windows we don't always manage to remove the watched directory, but it's unreliable
        if (!Platform.current().windows) {
            assert directoryRemoved
        }

        def expectedEvents = []
        if (Platform.current().macOs) {
            expectedEvents << change(INVALIDATED, watchedDir)
            if (ancestry == "watched directory") {
                expectedEvents << change(REMOVED, watchedDir)
            }
        } else if (Platform.current().linux) {
            expectedEvents << change(REMOVED, removedFile) << change(REMOVED, watchedDir)
        } else if (Platform.current().windows) {
            expectedEvents << change(MODIFIED, removedFile) << optionalChange(REMOVED, removedFile) << change(REMOVED, watchedDir)
        }

        then:
        expectEvents expectedEvents

        where:
        ancestry                            | removedDirectory
        "watched directory"                 | { it }
        "parent of watched directory"       | { it.parentFile }
        "grand-parent of watched directory" | { it.parentFile.parentFile }
    }

    @Issue("https://github.com/gradle/native-platform/issues/193")
    def "can rename watched directory"() {
        given:
        def watchedDirectory = new File(rootDir, "watched")
        watchedDirectory.mkdirs()
        startWatcher(watchedDirectory)

        when:
        watchedDirectory.renameTo(new File(rootDir, "newWatched"))
        waitForChangeEventLatency()
        then:
        if (Platform.current().linux) {
            expectLogMessage(WARNING, Pattern.compile("Unknown event 0x800 for ${Pattern.quote(watchedDirectory.absolutePath)}"))
        }
        noExceptionThrown()
    }

    @Requires({ Platform.current().windows })
    def "stops watching when path is moved"() {
        given:
        def watchedDir = new File(rootDir, "watched")
        assert watchedDir.mkdirs()
        def createdFile = new File(watchedDir, "created.txt")
        startWatcher(watchedDir)

        def renamedDir = new File(rootDir, "renamed")
        assert watchedDir.renameTo(renamedDir)

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == [watchedDir]

        when:
        assert createdFile.createNewFile()
        then:
        expectNoEvents()

        when:
        droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == []
    }

    @Requires({ Platform.current().linux || Platform.current().windows })
    def "stops watching when parent of path is moved"() {
        given:
        def parentDir = new File(rootDir, "parent")
        def watchedDir = new File(parentDir, "watched")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        def renamedDir = new File(rootDir, "renamed")
        assert parentDir.renameTo(renamedDir)

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == [watchedDir]

        when:
        def createdFile = new File(watchedDir, "created.txt")
        assert watchedDir.mkdirs()
        assert createdFile.createNewFile()
        then:
        expectNoEvents()

        when:
        droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == []
    }
}
