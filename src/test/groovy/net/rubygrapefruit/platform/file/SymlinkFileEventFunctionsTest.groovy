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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Ignore
import spock.lang.Requires

import static java.nio.file.Files.createSymbolicLink
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.MODIFIED

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class SymlinkFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    @Requires({ Platform.current().linux || Platform.current().macOs })
    def "can detect changes in symlinked watched directory"() {
        given:
        def canonicalFile = new File(rootDir, "modified.txt")
        createNewFile(canonicalFile)
        def watchedLink = new File(testDir, "linked")
        def watchedFile = new File(watchedLink, "modified.txt")
        createSymbolicLink(watchedLink.toPath(), rootDir.toPath())
        startWatcher(watchedLink)

        when:
        watchedFile << "change"

        then:
        expectEvents Platform.current().macOs
            ? change(MODIFIED, canonicalFile)
            : change(MODIFIED, watchedFile)
    }

    @Requires({ Platform.current().macOs })
    def "can watch directory via symlink and directly at the same time"() {
        given:
        def canonicalDir = new File(rootDir, "watchedDir")
        def canonicalFile = new File(canonicalDir, "modified.txt")
        canonicalDir.mkdirs()
        createNewFile(canonicalFile)
        def linkedDir = new File(rootDir, "linked")
        def linkedFile = new File(linkedDir, "modified.txt")
        createSymbolicLink(linkedDir.toPath(), canonicalDir.toPath())
        startWatcher(canonicalDir, linkedDir)

        when:
        linkedFile << "change"

        then:
        // TODO Figure out what to do on other OSs
        expectEvents Platform.current().macOs
            ? [change(MODIFIED, canonicalFile), change(MODIFIED, canonicalFile)]
            : []
    }

    @Requires({ Platform.current().linux })
    @Ignore("The behavior doesn't seem consistent across Linux variants")
    // Sometimes we get the same watch descriptor back when registering the watch with a different path,
    // other times not, but freeing the resulting watchers leads to errors
    def "fails when watching same directory both directly and via symlink"() {
        given:
        def canonicalDir = new File(rootDir, "watchedDir")
        canonicalDir.mkdirs()
        def linkedDir = new File(rootDir, "linked")
        createSymbolicLink(linkedDir.toPath(), canonicalDir.toPath())

        when:
        startWatcher(canonicalDir, linkedDir)

        then:
        def ex = thrown NativeException
        ex.message == "Already watching path: ${linkedDir.absolutePath}"

        when:
        startWatcher(linkedDir, canonicalDir)

        then:
        ex = thrown NativeException
        ex.message == "Already watching path: ${canonicalDir.absolutePath}"
    }

}
