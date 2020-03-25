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
import spock.lang.Requires
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions
import spock.util.environment.OperatingSystem

import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern

import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.CREATED
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.INVALIDATE
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.MODIFIED
import static net.rubygrapefruit.platform.file.FileWatcherCallback.Type.REMOVED

@Requires({ Platform.current().macOs || Platform.current().linux || Platform.current().windows })
class BasicFileEventFunctionsTest extends AbstractFileEventFunctionsTest {
    def "can start and stop watcher without watching any paths"() {
        when:
        startWatcher()

        then:
        noExceptionThrown()
    }

    def "can open and close watcher on a directory without receiving any events"() {
        when:
        startWatcher(rootDir)

        then:
        noExceptionThrown()
    }

    def "can detect file created"() {
        given:
        def createdFile = new File(rootDir, "created.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, createdFile)
        createNewFile(createdFile)

        then:
        expectedChanges.await()
    }

    def "can detect directory created"() {
        given:
        def createdDir = new File(rootDir, "created")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, createdDir)
        assert createdDir.mkdirs()

        then:
        expectedChanges.await()
    }

    def "can detect file removed"() {
        given:
        def removedFile = new File(rootDir, "removed.txt")
        createNewFile(removedFile)
        startWatcher(rootDir)

        when:
        // Windows reports the file as modified before removing it
        def expectedChanges = expectEvents Platform.current().windows
            ? [event(MODIFIED, removedFile), event(REMOVED, removedFile)]
            : [event(REMOVED, removedFile)]
        removedFile.delete()

        then:
        expectedChanges.await()
    }

    def "can detect directory removed"() {
        given:
        def removedDir = new File(rootDir, "removed")
        assert removedDir.mkdirs()
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(REMOVED, removedDir)
        removedDir.deleteDir()

        then:
        expectedChanges.await()
    }

    def "can detect file modified"() {
        given:
        def modifiedFile = new File(rootDir, "modified.txt")
        createNewFile(modifiedFile)
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(MODIFIED, modifiedFile)
        modifiedFile << "change"

        then:
        expectedChanges.await()
    }

    @Requires({ Platform.current().macOs })
    def "can detect file metadata modified"() {
        given:
        def modifiedFile = new File(rootDir, "modified.txt")
        createNewFile(modifiedFile)
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(MODIFIED, modifiedFile)
        modifiedFile.setReadable(false)

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents event(MODIFIED, modifiedFile)
        modifiedFile.setReadable(true)

        then:
        expectedChanges.await()
    }

    @Requires({ Platform.current().macOs })
    def "changing metadata immediately after creation is reported as modified"() {
        given:
        def createdFile = new File(rootDir, "file.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(MODIFIED, createdFile)
        createNewFile(createdFile)
        createdFile.setReadable(false)

        then:
        expectedChanges.await()
    }

    @Requires({ Platform.current().macOs })
    def "changing metadata doesn't mask content change"() {
        given:
        def modifiedFile = new File(rootDir, "modified.txt")
        modifiedFile.createNewFile()
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(MODIFIED, modifiedFile)
        modifiedFile.setReadable(false)
        modifiedFile << "change"

        then:
        expectedChanges.await()
    }

    @Requires({ Platform.current().macOs })
    def "changing metadata doesn't mask removal"() {
        given:
        def removedFile = new File(rootDir, "removed.txt")
        createNewFile(removedFile)
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(REMOVED, removedFile)
        removedFile.setReadable(false)
        assert removedFile.delete()

        then:
        expectedChanges.await()
    }

    def "can detect file renamed"() {
        given:
        def sourceFile = new File(rootDir, "source.txt")
        def targetFile = new File(rootDir, "target.txt")
        createNewFile(sourceFile)
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents Platform.current().windows
            ? [event(REMOVED, sourceFile), event(CREATED, targetFile), event(MODIFIED, targetFile, false)]
            : [event(REMOVED, sourceFile), event(CREATED, targetFile)]
        sourceFile.renameTo(targetFile)

        then:
        expectedChanges.await()
    }

    def "can detect file moved out"() {
        given:
        def outsideDir = new File(testDir, "outside")
        assert outsideDir.mkdirs()
        def sourceFileInside = new File(rootDir, "source-inside.txt")
        def targetFileOutside = new File(outsideDir, "target-outside.txt")
        createNewFile(sourceFileInside)
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(REMOVED, sourceFileInside)
        sourceFileInside.renameTo(targetFileOutside)

        then:
        expectedChanges.await()
    }

    def "can detect file moved in"() {
        given:
        def outsideDir = new File(testDir, "outside")
        assert outsideDir.mkdirs()
        def sourceFileOutside = new File(outsideDir, "source-outside.txt")
        def targetFileInside = new File(rootDir, "target-inside.txt")
        createNewFile(sourceFileOutside)
        startWatcher(rootDir)

        when:
        // On Windows we sometimes get a MODIFIED event after CREATED for some reason
        def expectedChanges = expectEvents Platform.current().windows
            ? [event(CREATED, targetFileInside), event(MODIFIED, targetFileInside, false)]
            : [event(CREATED, targetFileInside)]
        sourceFileOutside.renameTo(targetFileInside)

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from the same directory"() {
        given:
        def firstFile = new File(rootDir, "first.txt")
        def secondFile = new File(rootDir, "second.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, firstFile)
        createNewFile(firstFile)

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents event(CREATED, secondFile)
        waitForChangeEventLatency()
        createNewFile(secondFile)

        then:
        expectedChanges.await()
    }

    def "does not receive events from unwatched directory"() {
        given:
        def watchedFile = new File(rootDir, "watched.txt")
        def unwatchedDir = new File(testDir, "unwatched")
        assert unwatchedDir.mkdirs()
        def unwatchedFile = new File(unwatchedDir, "unwatched.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, watchedFile)
        createNewFile(unwatchedFile)
        createNewFile(watchedFile)
        // Let's make sure there are no events for the unwatched file,
        // and we don't just miss them because of timing
        waitForChangeEventLatency()

        then:
        expectedChanges.await()
    }

    // Apparently on macOS we can watch non-existent directories
    // TODO Should we fail for this?
    @IgnoreIf({ Platform.current().macOs })
    def "fails when watching non-existent directory"() {
        given:
        def missingDirectory = new File(rootDir, "missing")

        when:
        startWatcher(missingDirectory)

        then:
        def ex = thrown NativeException
        ex.message ==~ /Couldn't add watch.*: ${Pattern.quote(missingDirectory.absolutePath)}/
    }

    // Apparently on macOS we can watch files
    // TODO Should we fail for this?
    @IgnoreIf({ Platform.current().macOs })
    def "fails when watching file"() {
        given:
        def file = new File(rootDir, "file.txt")

        when:
        startWatcher(file)

        then:
        def ex = thrown NativeException
        ex.message ==~ /Couldn't add watch.*: ${Pattern.quote(file.absolutePath)}/
    }

    def "fails when watching directory twice"() {
        given:
        startWatcher(rootDir)

        when:
        watcher.startWatching(rootDir)

        then:
        def ex = thrown NativeException
        ex.message == "Already watching path: ${rootDir.absolutePath}"
    }

    def "can un-watch path that was not watched"() {
        given:
        startWatcher()

        when:
        watcher.stopWatching(rootDir)

        then:
        noExceptionThrown()
    }

    def "can un-watch watched directory twice"() {
        given:
        startWatcher(rootDir)
        watcher.stopWatching(rootDir)

        when:
        watcher.stopWatching(rootDir)

        then:
        noExceptionThrown()
    }

    def "does not receive events after directory is unwatched"() {
        given:
        def file = new File(rootDir, "first.txt")
        def callback = Mock(FileWatcherCallback)
        startWatcher(callback, rootDir)
        watcher.stopWatching(rootDir)

        when:
        createNewFile(file)

        then:
        0 * callback.pathChanged(_ as FileWatcherCallback.Type, _ as String)
        0 * _
    }

    def "can receive multiple events from multiple watched directories"() {
        given:
        def firstWatchedDir = new File(testDir, "first")
        assert firstWatchedDir.mkdirs()
        def firstFileInFirstWatchedDir = new File(firstWatchedDir, "first-watched.txt")
        def secondWatchedDir = new File(testDir, "second")
        assert secondWatchedDir.mkdirs()
        def secondFileInSecondWatchedDir = new File(secondWatchedDir, "sibling-watched.txt")
        startWatcher(firstWatchedDir, secondWatchedDir)

        when:
        def expectedChanges = expectEvents event(CREATED, firstFileInFirstWatchedDir)
        createNewFile(firstFileInFirstWatchedDir)

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents event(CREATED, secondFileInSecondWatchedDir)
        createNewFile(secondFileInSecondWatchedDir)

        then:
        expectedChanges.await()
    }

    @Requires({ !Platform.current().linux })
    def "can receive events from directory with different casing"() {
        given:
        def lowercaseDir = new File(rootDir, "watch-this")
        def uppercaseDir = new File(rootDir, "WATCH-THIS")
        def fileInLowercaseDir = new File(lowercaseDir, "lowercase.txt")
        def fileInUppercaseDir = new File(uppercaseDir, "UPPERCASE.TXT")
        uppercaseDir.mkdirs()
        startWatcher(lowercaseDir)

        when:
        def expectedChanges = expectEvents event(CREATED, fileInLowercaseDir.canonicalFile)
        createNewFile(fileInLowercaseDir)

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectEvents event(CREATED, fileInUppercaseDir.canonicalFile)
        createNewFile(fileInUppercaseDir)

        then:
        expectedChanges.await()
    }

    def "can handle exception in callback"() {
        given:
        def createdFile = new File(rootDir, "created.txt")
        def conditions = new AsyncConditions()
        when:
        startWatcher(new FileWatcherCallback() {
            @Override
            void pathChanged(FileWatcherCallback.Type type, String path) {
                throw new RuntimeException("Error")
            }

            @Override
            void reportError(Throwable ex) {
                conditions.evaluate {
                    assert ex instanceof NativeException
                    assert ex.message == "Caught java.lang.RuntimeException while calling callback: Error"
                }
            }
        }, rootDir)
        createNewFile(createdFile)

        then:
        conditions.await()
    }

    def "fails when stopped multiple times"() {
        given:
        def watcher = startNewWatcher(callback)
        watcher.close()

        when:
        watcher.close()

        then:
        def ex = thrown NativeException
        ex.message == "Closed already"
    }

    def "can be used multiple times"() {
        given:
        def firstFile = new File(rootDir, "first.txt")
        def secondFile = new File(rootDir, "second.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, firstFile)
        createNewFile(firstFile)

        then:
        expectedChanges.await()
        stopWatcher()

        when:
        startWatcher(rootDir)
        expectedChanges = expectEvents event(CREATED, secondFile)
        createNewFile(secondFile)

        then:
        expectedChanges.await()
    }

    def "can start multiple watchers"() {
        given:
        def firstRoot = new File(rootDir, "first")
        firstRoot.mkdirs()
        def secondRoot = new File(rootDir, "second")
        secondRoot.mkdirs()
        def firstFile = new File(firstRoot, "file.txt")
        def secondFile = new File(secondRoot, "file.txt")
        def firstCallback = new TestCallback()
        def secondCallback = new TestCallback()

        LOGGER.info("> Starting first watcher")
        def firstWatcher = startNewWatcher(firstCallback)
        firstWatcher.startWatching(firstRoot)
        LOGGER.info("> Starting second watcher")
        def secondWatcher = startNewWatcher(secondCallback)
        secondWatcher.startWatching(secondRoot)
        LOGGER.info("> Watchers started")

        when:
        def firstChanges = expectEvents firstCallback, event(CREATED, firstFile)
        createNewFile(firstFile)

        then:
        firstChanges.await()

        when:
        def secondChanges = expectEvents secondCallback, event(CREATED, secondFile)
        createNewFile(secondFile)

        then:
        secondChanges.await()

        cleanup:
        firstWatcher.close()
        secondWatcher.close()
    }

    @Requires({ !Platform.current().linux })
    def "can receive event about a non-direct descendant change"() {
        given:
        def subDir = new File(rootDir, "sub-dir")
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "watched-descendant.txt")
        startWatcher(rootDir)

        when:
        def expectedChanges = expectEvents event(CREATED, fileInSubDir)
        createNewFile(fileInSubDir)

        then:
        expectedChanges.await()
    }

    @Requires({ Platform.current().linux })
    def "does not receive event about a non-direct descendant change"() {
        given:
        def callback = Mock(FileWatcherCallback)
        def subDir = new File(rootDir, "sub-dir")
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "unwatched-descendant.txt")
        startWatcher(callback, rootDir)

        when:
        createNewFile(fileInSubDir)
        // Let's make sure there are no events occurring,
        // and we don't just miss them because of timing
        waitForChangeEventLatency()

        then:
        0 * callback.pathChanged(_ as FileWatcherCallback.Type, _ as String)
        0 * _
    }

    def "can watch directory with long path"() {
        given:
        def subDir = new File(rootDir, "long-path")
        4.times {
            subDir = new File(subDir, "X" * 200)
        }
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "watched-descendant.txt")
        startWatcher(subDir)

        when:
        def expectedChanges = expectEvents event(CREATED, fileInSubDir)
        createNewFile(fileInSubDir)

        then:
        expectedChanges.await()
    }

    @Unroll
    def "can watch directory with #type characters"() {
        Assume.assumeTrue(supported as boolean)

        given:
        def subDir = new File(rootDir, path)
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, path)
        startWatcher(subDir)

        when:
        def expectedChanges = expectEvents event(CREATED, fileInSubDir)
        createNewFile(fileInSubDir)

        then:
        expectedChanges.await()

        where:
        type             | path                     | supported
        "ASCII-only"     | "directory"              | true
        "Chinese"        | "输入文件"                   | true
        "four-byte UTF8" | "𠜎𠜱𠝹𠱓"               | true
        "Hungarian"      | "Dezső"                  | true
        "space"          | "test directory"         | true
        "zwnj"           | "test\u200cdirectory"    | true
        "newline"        | "test\ndirectory"        | Platform.current().macOs
        "URL-quoted"     | "test%<directory>#2.txt" | !Platform.current().windows
    }

    @Unroll
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
        def expectedChanges = expectEvents Platform.current().macOs
            ? [event(INVALIDATE, watchedDir)]
            : Platform.current().windows
            ? [event(MODIFIED, removedFile), event(REMOVED, removedFile, false), event(REMOVED, watchedDir)]
            : [event(REMOVED, removedFile), event(REMOVED, watchedDir)]
        def directoryDeleted = removedDir.deleteDir()

        then:
        expectedChanges.await()
        directoryDeleted == canDeleteDirectory

        where:
        ancestry                            | removedDirectory             | canDeleteDirectory
        "watched directory"                 | { it }                       | true
        "parent of watched directory"       | { it.parentFile }            | !OperatingSystem.current.windows
        "grand-parent of watched directory" | { it.parentFile.parentFile } | !OperatingSystem.current.windows
    }

    @Unroll
    def "can set log level by #action"() {
        given:
        def nativeLogger = Logger.getLogger(NativeLogger.name)
        def originalLevel = nativeLogger.level

        when:
        logging.clear()
        nativeLogger.level = Level.FINEST
        ensureLogLevelInvalidated(service)
        startWatcher()

        then:
        logging.messages.values().any { it == Level.FINE }

        when:
        stopWatcher()
        logging.clear()
        nativeLogger.level = Level.WARNING
        ensureLogLevelInvalidated(service)
        startWatcher()

        then:
        !logging.messages.values().any { it == Level.FINE }

        cleanup:
        nativeLogger.level = originalLevel

        where:
        action                                    | ensureLogLevelInvalidated
        "invalidating the log level cache"        | { AbstractFileEventFunctions service -> service.invalidateLogLevelCache() }
        "waiting for log level cache to time out" | { Thread.sleep(1500) }
    }
}
