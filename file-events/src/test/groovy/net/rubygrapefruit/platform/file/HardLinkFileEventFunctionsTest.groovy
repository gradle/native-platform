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

import static java.nio.file.Files.createLink
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.LINUX
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.MAC_OS
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.OTHERWISE
import static net.rubygrapefruit.platform.file.AbstractFileEventFunctionsTest.PlatformType.WINDOWS
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.CREATED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.MODIFIED
import static net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType.REMOVED

/**
 * None of the operating systems deliver notifications about changing watched content via hard links
 * that exist in non-watched locations:
 *
 * <dl>
 *     <dt>Linux</dt>
 *     <dd><em>"Watching a directory with inotify does not trigger events on hardlinks"</em>
 *     – <a href="https://bugzilla.kernel.org/show_bug.cgi?id=115571">link</a></dd>
 *
 *     <dt>macOS</dt>
 *     <dd><em>"Symbolic links work well, the problem is only related with hardlinks. Hardlinks are not possible for folders,
 *     so FSEvents is a good way to monitor the creation/deletion and move of files inside the given folder.
 *     In order to monitor file modification, I switched to Kernel Queues, which allows to monitor properly hardlinks."</em>
 *     – <a href="https://stackoverflow.com/questions/64101560/how-to-detect-fsevent-if-a-file-is-modified-through-a-hard-symlink">link</a></dd>
 *
 *     <dt>Windows</dt>
 *     <dd><em>"[...] since these changes have no effect on the directory, they are not recognized
 *     by ReadDirectoryChangesW. The ReadDirectoryChangesW function tells you about changes to
 *     the directory; if something happens that doesn’t change the directory, then
 *     ReadDirectoryChangesW will just shrug its shoulders and say, “Hey, not my job.”"</em>
 *     – <a href="https://devblogs.microsoft.com/oldnewthing/20110812-00/?p=9913">link</a></dd>
 * </dl>
 */
@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class HardLinkFileEventFunctionsTest extends AbstractFileEventFunctionsTest {

    def "can detect hard link created"() {
        given:
        def target = new File(rootDir, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        startWatcher(rootDir)

        when:
        createHardLink(link, target)

        then:
        // Windows sometimes reports a modification after the creation
        // macOS reports a change to the directory containing the original file
        expectEvents byPlatform(
            (WINDOWS): [change(CREATED, link), optionalChange(MODIFIED, link)],
            (MAC_OS): [change(MODIFIED, rootDir)],
            (LINUX): [change(CREATED, link)]
        )
    }

    def "does not detect hard link created outside watched hierarchy"() {
        given:
        def watched = new File(rootDir, "watched")
        watched.mkdirs()
        def target = new File(watched, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        startWatcher(watched)

        when:
        createHardLink(link, target)

        then:
        // macOS reports a change to the directory containing the original file
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, watched)],
            (OTHERWISE): []
        )
    }

    def "can detect hard link modified"() {
        given:
        def target = new File(rootDir, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        createHardLink(link, target)
        startWatcher(rootDir)

        when:
        link.text = "modified"

        then:
        // On Windows we seem to be getting multiple MODIFIED events
        // On Linux we _sometimes_ seem to be getting multiple events
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, link)],
            (WINDOWS): [change(MODIFIED, link)] * 2,
            (LINUX): [change(MODIFIED, link), optionalChange(MODIFIED, link)]
        )
    }

    def "does not detect hard link modified outside watched hierarchy"() {
        given:
        def watched = new File(rootDir, "watched")
        watched.mkdirs()
        def target = new File(watched, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        createHardLink(link, target)
        startWatcher(watched)

        when:
        link.text = "modified"

        then:
        expectNoEvents()

        when:
        target.text = "modified2"

        then:
        // On Windows we seem to be getting multiple MODIFIED events
        // On Linux we _sometimes_ seem to be getting multiple events
        expectEvents byPlatform(
            (MAC_OS): [change(MODIFIED, target)],
            (WINDOWS): [change(MODIFIED, target)] * 2,
            (LINUX): [change(MODIFIED, target), optionalChange(MODIFIED, target)]
        )
    }

    def "can detect hard link removed"() {
        given:
        def target = new File(rootDir, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        createHardLink(link, target)
        startWatcher(rootDir)

        when:
        link.delete()

        then:
        // Windows sometimes reports a modification before the removal
        expectEvents byPlatform(
            (WINDOWS): [optionalChange(MODIFIED, link), change(REMOVED, link)],
            (OTHERWISE): [change(REMOVED, link)]
        )
    }

    def "does not detect hard link removed outside watched hierarchy"() {
        given:
        def watched = new File(rootDir, "watched")
        watched.mkdirs()
        def target = new File(watched, "target")
        target.createNewFile()
        def link = new File(rootDir, "link")
        createHardLink(link, target)
        startWatcher(watched)

        when:
        link.delete()

        then:
        expectNoEvents()
    }

    private void createHardLink(File linked, File target) {
        LOGGER.info("> Creating link to ${shorten(target)} in ${shorten(linked)}")
        createLink(linked.toPath(), target.toPath())
        LOGGER.info("< Created link to ${shorten(target)} in ${shorten(linked)}")
    }
}
