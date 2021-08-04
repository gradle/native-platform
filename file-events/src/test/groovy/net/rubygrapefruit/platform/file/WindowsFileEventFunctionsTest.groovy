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

@Unroll
@Requires({ Platform.current().windows })
class WindowsFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    @Requires({ Platform.current().windows })
    def "reports events in new location when watched directory has been moved"() {
        given:
        def watchedDir = new File(rootDir, "watched")
        assert watchedDir.mkdirs()
        def renamedDir = new File(rootDir, "renamed")
        def createdFile = new File(renamedDir, "created.txt")
        startWatcher(watchedDir)

        watchedDir.renameTo(renamedDir)

        when:
        createdFile.createNewFile()

        then:
        expectEvents change(CREATED, createdFile)
    }

    @Requires({ Platform.current().windows })
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
}
