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
import spock.lang.Unroll

import static java.nio.file.Files.createSymbolicLink
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.MAC_OS
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.OTHERWISE
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.WINDOWS
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.MODIFIED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.REMOVED

@Unroll
@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class SymlinkFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    def "can detect symlink to #description created"() {
        given:
        def target = new File(rootDir, "target")
        initialize(target)
        def link = new File(rootDir, "link")
        startWatcher(rootDir)

        when:
        createSymlink(link, target)

        then:
        // Windows sometimes reports a modification after the creation
        expectEvents byPlatform(
            (WINDOWS): [change(CREATED, link), optionalChange(MODIFIED, link)],
            (OTHERWISE): [change(CREATED, link)]
        )

        where:
        description    | initialize
        "regular file" | { File file -> assert file.createNewFile() }
        "directory"    | { File file -> assert file.mkdirs() }
        "missing file" | { File file -> }
    }

    def "can detect symlink to #description removed"() {
        given:
        def target = new File(rootDir, "target")
        initialize(target)
        def link = new File(rootDir, "link")
        createSymlink(link, target)
        startWatcher(rootDir)

        when:
        link.delete()

        then:
        // Windows sometimes reports a modification before the removal
        expectEvents byPlatform(
            (WINDOWS):   [optionalChange(MODIFIED, link), change(REMOVED, link)],
            (OTHERWISE): [change(REMOVED, link)]
        )

        where:
        description    | initialize
        "regular file" | { File file -> assert file.createNewFile() }
        "directory"    | { File file -> assert file.mkdirs() }
        "missing file" | { File file -> }
    }

    def "does not detect symlink target #description created"() {
        given:
        def contentRoot = new File(rootDir, "contentDir")
        contentRoot.mkdirs()
        def watchedRoot = new File(rootDir, "watchedDir")
        watchedRoot.mkdirs()
        def target = new File(contentRoot, "target")
        def link = new File(watchedRoot, "link")
        createSymlink(link, target)
        startWatcher(watchedRoot)

        when:
        initialize(target)

        then:
        expectNoEvents()

        where:
        description    | initialize
        "regular file" | { File file -> assert file.createNewFile() }
        "directory"    | { File file -> assert file.mkdirs() }
    }

    def "can detect changes in symlinked watched directory"() {
        given:
        def canonicalFile = new File(rootDir, "modified.txt")
        createNewFile(canonicalFile)
        def watchedLink = new File(testDir, "linked")
        def watchedFile = new File(watchedLink, "modified.txt")
        createSymlink(watchedLink, rootDir)
        startWatcher(watchedLink)

        when:
        watchedFile << "change"

        then:
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, canonicalFile)],
            (OTHERWISE): [change(MODIFIED, watchedFile)]
        )
    }

    def "can detect changes if parent of watched directory is a symlink"() {
        given:
        def canonicalFile = new File(rootDir, "watchedRoot/modified.txt")
        canonicalFile.parentFile.mkdirs()
        createNewFile(canonicalFile)
        def linkedParent = new File(testDir, "parent")
        def watchedDir = new File(linkedParent, "watchedRoot")
        def watchedFile = new File(watchedDir, "modified.txt")
        createSymlink(linkedParent, rootDir)
        startWatcher(watchedDir)

        when:
        watchedFile << "change"

        then:
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, canonicalFile)],
            (OTHERWISE): [change(MODIFIED, watchedFile)]
        )
    }

    @Requires({ Platform.current().macOs || Platform.current().windows })
    def "can watch directory via symlink and directly at the same time"() {
        given:
        def canonicalDir = new File(rootDir, "watchedDir")
        def canonicalFile = new File(canonicalDir, "modified.txt")
        canonicalDir.mkdirs()
        createNewFile(canonicalFile)
        def linkedDir = new File(rootDir, "linked")
        def linkedFile = new File(linkedDir, "modified.txt")
        createSymlink(linkedDir, canonicalDir)
        startWatcher(canonicalDir, linkedDir)

        when:
        linkedFile << "change"

        then:
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, canonicalFile), change(MODIFIED, canonicalFile)],
            (OTHERWISE): [change(MODIFIED, linkedFile), change(MODIFIED, canonicalFile)]
        )
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
        createSymlink(linkedDir, canonicalDir)

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

    private void createSymlink(File linked, File target) {
        LOGGER.info("> Creating link to ${shorten(target)} in ${shorten(linked)}")
        createSymbolicLink(linked.toPath(), target.toPath())
        LOGGER.info("< Created link to ${shorten(target)} in ${shorten(linked)}")
    }

}
