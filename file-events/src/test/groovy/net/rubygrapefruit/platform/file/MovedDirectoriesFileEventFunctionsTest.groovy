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

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Unroll

import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.util.regex.Pattern

import static java.util.logging.Level.WARNING
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
        Files.move(watchedDir.toPath(), renamedDir.toPath())

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == [watchedDir]

        when:
        assert watchedDir.mkdir()
        assert createdFile.createNewFile()
        then:
        expectNoEvents()

        when:
        droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == []
    }

    @Requires({ Platform.current().linux })
    def "stops watching when parent of watched directory is moved on Linux"() {
        given:
        def parentDir = new File(rootDir, "parent")
        def watchedDir = new File(parentDir, "watched")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        def renamedDir = new File(rootDir, "renamed")
        Files.move(parentDir.toPath(), renamedDir.toPath())

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths([watchedDir])
        then:
        droppedPaths == [watchedDir]

        when:
        def createdFile = new File(watchedDir, "created.txt")
        assert watchedDir.mkdirs()
        assert createdFile.createNewFile()
        then:
        expectNoEvents()

        when:
        droppedPaths = watcher.stopWatchingMovedPaths([watchedDir])
        then:
        droppedPaths == []
    }

    @Requires({ Platform.current().macOs })
    def "keeps watching when parent of watched directory is moved on macOS"() {
        given:
        def parentDir = new File(rootDir, "parent")
        def watchedDir = new File(parentDir, "watched")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        def renamedDir = new File(rootDir, "renamed")

        when:
        Files.move(parentDir.toPath(), renamedDir.toPath())
        then:
        expectEvents change(INVALIDATED, watchedDir)

        when:
        assert watchedDir.mkdirs()
        then:
        expectEvents change(INVALIDATED, watchedDir), change(CREATED, watchedDir)

        when:
        def createdFile = new File(watchedDir, "created.txt")
        assert createdFile.createNewFile()
        then:
        expectEvents change(CREATED, createdFile)
    }

    @Requires({ Platform.current().windows })
    def "cannot move parent of watched directory on Windows"() {
        given:
        def parentDir = new File(rootDir, "parent")
        def watchedDir = new File(parentDir, "watched")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        def renamedDir = new File(rootDir, "renamed")

        when:
        Files.move(parentDir.toPath(), renamedDir.toPath())
        then:
        thrown(AccessDeniedException)
    }
}
