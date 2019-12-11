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

import groovy.transform.EqualsAndHashCode
import net.rubygrapefruit.platform.internal.Platform
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions

import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.*

abstract class AbstractFileEventsTest extends Specification {
    @Rule
    TemporaryFolder tmpDir
    def callback = new TestCallback()
    File dir
    File fileInDir

    def setup() {
        dir = tmpDir.newFolder()
        fileInDir = new File(dir, "watched.txt")
    }

    def cleanup() {
        stopWatcher()
    }

    def "can open and close watcher on a directory without receiving any events"() {
        when:
        startWatcher(dir)

        then:
        noExceptionThrown()
    }

    def "can detect file created"() {
        given:
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can detect file deleted"() {
        given:
        fileInDir.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(REMOVED, fileInDir)
        fileInDir.delete()

        then:
        expectedChanges.await()
    }

    def "can detect file modified"() {
        given:
        fileInDir.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(MODIFIED, fileInDir)
        fileInDir << "change"

        then:
        expectedChanges.await()
    }

    def "can detect file renamed"() {
        given:
        fileInDir.createNewFile()
        def renamedFileInDir = new File(dir, "renamed.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(REMOVED, fileInDir)
        fileInDir.renameTo(renamedFileInDir)

        then:
        expectedChanges.await()
    }

    def "can detect file moved out"() {
        given:
        def siblingDir = tmpDir.newFolder()
        fileInDir.createNewFile()
        def fileInSiblingDir = new File(siblingDir, "moved-out.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(REMOVED, fileInDir)
        fileInDir.renameTo(fileInSiblingDir)

        then:
        expectedChanges.await()
    }

    def "can detect file moved in"() {
        given:
        def siblingDir = tmpDir.newFolder()
        def fileInSiblingDir = new File(siblingDir, "moved-in.txt")
        fileInSiblingDir.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInSiblingDir.renameTo(fileInDir)

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from the same directory"() {
        given:
        def otherFileInDir = new File(dir, "also-watched.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChangeIsDetected(ADDED, otherFileInDir)
        waitForChangeEventLatency()
        otherFileInDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "does not receive events from unwatched directory"() {
        given:
        def siblingDir = tmpDir.newFolder()
        def fileInSiblingDir = new File(siblingDir, "unwatched.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInSiblingDir.createNewFile()
        fileInDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from multiple watched directories"() {
        given:
        def siblingDir = tmpDir.newFolder()
        def fileInSiblingDir = new File(siblingDir, "sibling-watched.txt")

        startWatcher(dir, siblingDir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInSiblingDir)
        fileInSiblingDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive events from directory with different casing"() {
        def subDirLowercase = new File(dir, "watch-this")
        def subDirUppercase = new File(dir, "WATCH-THIS")
        def fileInLowercaseSubDir = new File(subDirLowercase, "lowercase.txt")
        def fileInUppercaseSubDir = new File(subDirUppercase, "UPPERCASE.TXT")

        subDirUppercase.mkdirs()

        given:
        startWatcher(subDirLowercase)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInLowercaseSubDir)
        fileInLowercaseSubDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInUppercaseSubDir)
        fileInUppercaseSubDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can be started once and stopped multiple times"() {
        given:
        startWatcher(dir)

        when:
        // TODO There's a race condition in starting the macOS watcher thread
        Thread.sleep(100)
        watcher.close()
        watcher.close()

        then:
        noExceptionThrown()
    }

    def "can be used multiple times"() {
        given:
        def otherFileInDir = new File(dir, "also-watched.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()
        stopWatcher()

        when:
        startWatcher(dir)
        expectedChanges = expectThat pathChangeIsDetected(ADDED, otherFileInDir)
        otherFileInDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive event about a non-direct descendant change"() {
        given:
        def subDir = new File(dir, "sub-dir")
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "watched-descendant.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInSubDir)
        fileInSubDir.createNewFile()

        then:
        expectedChanges.await()
    }

    // Not yet implemented properly for Windows, works on macOS
    @IgnoreIf({ Platform.current().windows })
    def "can watch directory with long path"() {
        given:
        def subDir = new File(dir, "long-path")
        4.times {
            subDir = new File(subDir, "X" * 200)
        }
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "watched-descendant.txt")
        startWatcher(subDir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInSubDir)
        fileInSubDir.createNewFile()

        then:
        expectedChanges.await()
    }

    @Unroll
    def "can watch directory with #type characters"() {
        Assume.assumeTrue(supported)

        given:
        def subDir = new File(dir, path)
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, path)
        startWatcher(subDir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(ADDED, fileInSubDir)
        fileInSubDir.createNewFile()

        then:
        expectedChanges.await()

        where:
        type         | path                     | supported
        "ASCII-only" | "directory"              | true
        "Chinese"    | "输入文件"                   | true
        "Hungarian"  | "Dezső"                  | true
        "space"      | "test directory"         | true
        "zwnj"       | "test\u200cdirectory"    | true
        "URL-quoted" | "test%<directory>#2.txt" | !Platform.current().windows
    }

    protected abstract void startWatcher(File... roots)

    protected abstract void stopWatcher()

    private AsyncConditions expectThat(List<Event> events) {
        return callback.expect(events)
    }

    private List<Event> pathChangeIsDetected(FileWatcherCallback.Type type, File... paths) {
        return paths.collect { path ->
            println "> Expecting ${path.canonicalPath} $type"
            return resolveExpectedChange(type, path.canonicalFile)
        }
    }

    protected abstract FileEvent resolveExpectedChange(FileWatcherCallback.Type type, File changedFile)

    private static class TestCallback implements FileWatcherCallback {
        private AsyncConditions conditions
        private Collection<Event> expectedEvents

        AsyncConditions expect(Collection<Event> events) {
            this.conditions = new AsyncConditions()
            this.expectedEvents = new ArrayList<>(events)
            return conditions
        }

        @Override
        void pathChanged(Type type, String path) {
            println "> Changed: $path $type"
            def expectedEvent = new FileEvent(type, new File(path).canonicalFile)
            def found = expectedEvents.remove(expectedEvent)
            assert found, "Unexpected change $path $type"
            evaluateIfAllEventsAreDone()
        }

        private void evaluateIfAllEventsAreDone() {
            if (expectedEvents.empty) {
                conditions.evaluate {}
            }
        }

        @Override
        void overflow() {
            println "> Overflow!"
            assert expectedEvents.remove(OverflowEvent.INSTANCE), "Unexpected overflow"
            evaluateIfAllEventsAreDone()
        }
    }

    protected interface Event {}

    @EqualsAndHashCode
    @SuppressWarnings("unused")
    protected static class FileEvent implements Event {
        final FileWatcherCallback.Type type
        final File file

        FileEvent(FileWatcherCallback.Type type, File file) {
            this.type = type
            this.file = file
        }
    }

    protected static class OverflowEvent implements Event {
        static final INSTANCE = new OverflowEvent()

        private OverflowEvent() {}
    }

    protected abstract void waitForChangeEventLatency()
}
