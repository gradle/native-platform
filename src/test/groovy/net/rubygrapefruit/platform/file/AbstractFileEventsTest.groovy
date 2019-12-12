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
import spock.lang.Ignore
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

    def setup() {
        dir = tmpDir.newFolder()
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
        def createdFile = new File(dir, "created.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents created(createdFile)
        createdFile.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can detect file removed"() {
        given:
        def removedFile = new File(dir, "removed.txt")
        removedFile.createNewFile()
        // TODO Why does Windows report the modification?
        def expectedEvents = Platform.current().windows
            ? [modified(removedFile), removed(removedFile)]
            : [removed(removedFile)]
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents expectedEvents
        removedFile.delete()

        then:
        expectedChanges.await()
    }

    def "can detect file modified"() {
        given:
        def modifiedFile = new File(dir, "modified.txt")
        modifiedFile.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents modified(modifiedFile)
        modifiedFile << "change"

        then:
        expectedChanges.await()
    }

    def "can detect file renamed"() {
        given:
        def sourceFile = new File(dir, "source.txt")
        def targetFile = new File(dir, "target.txt")
        sourceFile.createNewFile()
        // TODO Why doesn't Windows report the creation of the target file?
        def expectedEvents = Platform.current().windows
            ? [removed(sourceFile)]
            : [removed(sourceFile), created(targetFile)]
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents expectedEvents
        sourceFile.renameTo(targetFile)

        then:
        expectedChanges.await()
    }

    def "can detect file moved out"() {
        given:
        def outsideDir = tmpDir.newFolder()
        def sourceFileInside = new File(dir, "source-inside.txt")
        def targetFileOutside = new File(outsideDir, "target-outside.txt")
        sourceFileInside.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents removed(sourceFileInside)
        sourceFileInside.renameTo(targetFileOutside)

        then:
        expectedChanges.await()
    }

    def "can detect file moved in"() {
        given:
        def outsideDir = tmpDir.newFolder()
        def sourceFileOutside = new File(outsideDir, "source-outside.txt")
        def targetFileInside = new File(dir, "target-inside.txt")
        sourceFileOutside.createNewFile()
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents created(targetFileInside)
        sourceFileOutside.renameTo(targetFileInside)

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from the same directory"() {
        given:
        def firstFile = new File(dir, "first.txt")
        def secondFile = new File(dir, "second.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents created(firstFile)
        firstFile.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents created(secondFile)
        waitForChangeEventLatency()
        secondFile.createNewFile()

        then:
        expectedChanges.await()
    }

    def "does not receive events from unwatched directory"() {
        given:
        def watchedFile = new File(dir, "watched.txt")
        def unwatchedDir = tmpDir.newFolder()
        def unwatchedFile = new File(unwatchedDir, "unwatched.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents created(watchedFile)
        unwatchedFile.createNewFile()
        watchedFile.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from multiple watched directories"() {
        given:
        def firstFileInFirstWatchedDir = new File(dir, "first-watched.txt")
        def secondWatchedDir = tmpDir.newFolder()
        def secondFileInSecondWatchedDir = new File(secondWatchedDir, "sibling-watched.txt")
        startWatcher(dir, secondWatchedDir)

        when:
        def expectedChanges = expectEvents created(firstFileInFirstWatchedDir)
        firstFileInFirstWatchedDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents created(secondFileInSecondWatchedDir)
        secondFileInSecondWatchedDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive events from directory with different casing"() {
        given:
        def lowercaseDir = new File(dir, "watch-this")
        def uppercaseDir = new File(dir, "WATCH-THIS")
        def fileInLowercaseDir = new File(lowercaseDir, "lowercase.txt")
        def fileInUppercaseDir = new File(uppercaseDir, "UPPERCASE.TXT")
        uppercaseDir.mkdirs()
        startWatcher(lowercaseDir)

        when:
        def expectedChanges = expectEvents created(fileInLowercaseDir)
        fileInLowercaseDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents created(fileInUppercaseDir)
        fileInUppercaseDir.createNewFile()

        then:
        expectedChanges.await()
    }

    // TODO Handle exceptions happening in callbacks
    @Ignore("Exceptions in callbacks are now silently ignored")
    def "can handle exception in callback"() {
        given:
        def error = new RuntimeException("Error")
        def createdFile = new File(dir, "created.txt")
        def conditions = new AsyncConditions()
        when:
        startWatcher({ type, path ->
            try {
                throw error
            } finally {
                conditions.evaluate {}
            }
        }, dir)
        createdFile.createNewFile()
        conditions.await()

        then:
        noExceptionThrown()
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
        def firstFile = new File(dir, "first.txt")
        def secondFile = new File(dir, "second.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectEvents created(firstFile)
        firstFile.createNewFile()

        then:
        expectedChanges.await()
        stopWatcher()

        when:
        startWatcher(dir)
        expectedChanges = expectEvents created(secondFile)
        secondFile.createNewFile()

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
        def expectedChanges = expectEvents created(fileInSubDir)
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
        def expectedChanges = expectEvents created(fileInSubDir)
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
        def expectedChanges = expectEvents created(fileInSubDir)
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

    protected abstract void startWatcher(FileWatcherCallback callback = this.callback, File... roots)

    protected abstract void stopWatcher()

    protected AsyncConditions expectEvents(FileEvent... events) {
        expectEvents(events as List)
    }

    protected AsyncConditions expectEvents(List<FileEvent> events) {
        return callback.expect(events)
    }

    protected static FileEvent created(File file) {
        return new FileEvent(CREATED, file)
    }

    protected static FileEvent removed(File file) {
        return new FileEvent(REMOVED, file)
    }

    protected static FileEvent modified(File file) {
        return new FileEvent(MODIFIED, file)
    }

    private static class TestCallback implements FileWatcherCallback {
        private AsyncConditions conditions
        private Collection<FileEvent> expectedEvents

        AsyncConditions expect(List<FileEvent> events) {
            events.each { event ->
                println "> Expecting $event"
            }
            this.conditions = new AsyncConditions()
            this.expectedEvents = new ArrayList<>(events)
            return conditions
        }

        @Override
        void pathChanged(Type type, String path) {
            handleEvent(new FileEvent(type, new File(path).canonicalFile))
        }

        private void handleEvent(FileEvent event) {
            println "> Received  $event"
            if (!expectedEvents.remove(event)) {
                conditions.evaluate {
                    throw new RuntimeException("Unexpected event $event")
                }
            }
            if (expectedEvents.empty) {
                conditions.evaluate {}
            }
        }
    }

    @EqualsAndHashCode
    @SuppressWarnings("unused")
    protected static class FileEvent {
        final FileWatcherCallback.Type type
        final File file

        FileEvent(FileWatcherCallback.Type type, File file) {
            this.type = type
            this.file = file.canonicalFile
        }

        @Override
        String toString() {
            return "$type $file"
        }
    }

    protected abstract void waitForChangeEventLatency()
}
