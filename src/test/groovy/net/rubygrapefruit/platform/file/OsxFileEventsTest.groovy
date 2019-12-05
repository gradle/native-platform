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
import net.rubygrapefruit.platform.internal.jni.DefaultOsxFileEventFunctions
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Timeout

@IgnoreIf({ !Platform.current().macOs })
@Timeout(20)
class OsxFileEventsTest extends Specification {
    @Rule
    TemporaryFolder tmpDir
    final DefaultOsxFileEventFunctions fileEvents = Native.get(DefaultOsxFileEventFunctions.class)

    def "caches file events instance"() {
        expect:
        Native.get(DefaultOsxFileEventFunctions.class) is fileEvents
    }

    def "can open and close watch on a directory without receiving any events"() {
        def changes = startWatch()

        expect:
        changes.empty

        cleanup:
        stopWatch()
    }

    def "can open and close watch on a directory receiving an event"() {
        given:
        def dir = tmpDir.newFolder()
        def changes = startWatch(dir.absolutePath)

        when:
        new File(dir, "a.txt").createNewFile()
        waitForFileSystem()

        then:
        changes == [dir.canonicalPath + "/"]

        cleanup:
        stopWatch()
    }

    def "can open and close watch on a directory receiving multiple events"() {
        given:
        def dir = tmpDir.newFolder()
        def changes = startWatch(dir.absolutePath)

        when:
        new File(dir, "a.txt").createNewFile()
        new File(dir, "b.txt").createNewFile()
        waitForFileSystem()

        then:
        changes == [dir.canonicalPath + "/", dir.canonicalPath + "/"]

        cleanup:
        stopWatch()
    }

    def "can open and close watch on multiple directories receiving multiple events"() {
        given:
        def dir1 = tmpDir.newFolder()
        def dir2 = tmpDir.newFolder()
        def changes = startWatch(dir1.absolutePath, dir2.absolutePath)

        when:
        new File(dir1, "a.txt").createNewFile()
        new File(dir2, "b.txt").createNewFile()
        waitForFileSystem()

        then:
        changes == [dir1.canonicalPath + "/", dir2.canonicalPath + "/"]

        cleanup:
        stopWatch()
    }

    def "can be started once and stopped multiple times"() {
        given:
        def dir = tmpDir.newFolder()
        def changes = startWatch(dir.absolutePath)

        when:
        new File(dir, "a.txt").createNewFile()
        waitForFileSystem()

        then:
        changes == [dir.canonicalPath + "/"]

        when:
        changes.clear()
        stopWatch()

        then:
        changes.empty

        when:
        stopWatch()

        then:
        changes.empty
    }

    def "can be used multiple times"() {
        given:
        def dir = tmpDir.newFolder()
        def changes = startWatch(dir.absolutePath)

        when:
        new File(dir, "a.txt").createNewFile()
        waitForFileSystem()
        stopWatch()

        then:
        changes == [dir.canonicalPath + "/"]

        when:
        dir = tmpDir.newFolder()
        changes = startWatch(dir.absolutePath)
        new File(dir, "a.txt").createNewFile()
        waitForFileSystem()
        stopWatch()

        then:
        changes == [dir.canonicalPath + "/"]
    }

    private List<String> startWatch(String... paths) {
        def changes = []
        fileEvents.startWatch(paths as List, { changes.add(it) })
        return changes
    }

    private void stopWatch() {
        fileEvents.stopWatch()
    }

    // TODO: this is not great, as it leads to flaky tests. Figure out a better way.
    private static void waitForFileSystem() {
        Thread.sleep(20)
    }
}
