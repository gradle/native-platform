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
import spock.lang.Requires
import spock.lang.Unroll

import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.REMOVED

@Unroll
@Requires({ Platform.current().windows })
class WindowsFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    def "stops watching and reports watched directory as removed after it has been moved and an event is received"() {
        given:
        def watchedDir = new File(rootDir, "watched")
        assert watchedDir.mkdirs()
        def renamedDir = new File(rootDir, "renamed")
        def createdFile = new File(renamedDir, "created.txt")
        startWatcher(watchedDir)

        when:
        watchedDir.renameTo(renamedDir)
        then:
        expectNoEvents()

        when:
        createdFile.createNewFile()
        then:
        expectEvents change(REMOVED, watchedDir)

        when:
        assert createdFile.delete()
        then:
        expectNoEvents()
    }

    def "reports changes on subst drive"() {
        given:
        subst("G:", rootDir)
        def watchedDir = new File("G:\\watched")
        def createdFile = new File(watchedDir, "created.txt")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        when:
        createNewFile(createdFile)
        then:
        expectEvents change(CREATED, createdFile)

        cleanup:
        unsubst("G:")
    }

    def "drops moved locations"() {
        given:
        def watchedDir = new File(rootDir, "watched")
        assert watchedDir.mkdirs()
        def renamedDir = new File(rootDir, "renamed")
        def createdFile = new File(renamedDir, "created.txt")
        startWatcher(watchedDir)

        watchedDir.renameTo(renamedDir)

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == [watchedDir]

        when:
        createdFile.createNewFile()
        then:
        expectNoEvents()

        when:
        droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == []
    }

    def "does not drop subst drive as moved"() {
        given:
        subst("G:", rootDir)
        def watchedDir = new File("G:\\watched")
        assert watchedDir.mkdirs()
        startWatcher(watchedDir)

        when:
        def droppedPaths = watcher.stopWatchingMovedPaths()
        then:
        droppedPaths == []

        cleanup:
        unsubst("G:")
    }

    def subst(String substDrive, File substPath) {
        ["CMD", "/C", "SUBST", substDrive, substPath.absolutePath].execute()
            .waitForProcessOutput(System.out, System.err)
    }

    def unsubst(String substDrive) {
        ["CMD", "/C", "SUBST", "/D", substDrive].execute()
            .waitForProcessOutput(System.out, System.err)
    }
}
