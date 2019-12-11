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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions

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

    def "can receive an event"() {
        given:
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()
    }

    def "can receive multiple events from the same directory"() {
        given:
        def otherFileInDir = new File(dir, "also-watched.txt")
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChangeIsDetected(otherFileInDir)
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
        def expectedChanges = expectThat pathChangeIsDetected(fileInDir)
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
        def expectedChanges = expectThat pathChangeIsDetected(fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChangeIsDetected(fileInSiblingDir)
        fileInSiblingDir.createNewFile()

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
        def expectedChanges = expectThat pathChangeIsDetected(fileInDir)
        fileInDir.createNewFile()

        then:
        expectedChanges.await()
        stopWatcher()

        when:
        startWatcher(dir)
        expectedChanges = expectThat pathChangeIsDetected(otherFileInDir)
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
        def expectedChanges = expectThat pathChangeIsDetected(fileInSubDir)
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
        println "Watching (${subDir.canonicalPath.length()}) $subDir"
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, "watched-descendant.txt")
        startWatcher(subDir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(fileInSubDir)
        fileInSubDir.createNewFile()

        then:
        expectedChanges.await()
    }

    @Unroll
    def "can watch directory with #type characters"() {
        given:
        def subDir = new File(dir, path)
        println "Watching (${subDir.canonicalPath.length()}) $subDir"
        subDir.mkdirs()
        def fileInSubDir = new File(subDir, path)
        startWatcher(subDir)

        when:
        def expectedChanges = expectThat pathChangeIsDetected(fileInSubDir)
        fileInSubDir.createNewFile()

        then:
        expectedChanges.await()

        where:
        type          | path
        "ascii-only"  | "directory"
        "chinese"     | "输入文件"
        "hungarian"   | "Dezső"
        "space"       | "test directory"
        "zwnj"        | "test\u200cdirectory"
        "url-quoted"  | "test%<directory>#2.txt"
    }

    protected abstract void startWatcher(File... roots)

    protected abstract void stopWatcher()

    private AsyncConditions expectThat(FileWatcherCallback delegateCallback) {
        return callback.expectCallback(delegateCallback)
    }

    private FileWatcherCallback pathChangeIsDetected(File path) {
        return { changedPath ->
            String expected = resolveExpectedChange(path.canonicalFile)
            String actual
            try {
                actual = new File(changedPath).canonicalPath
            } catch (IOException ex) {
                throw new AssertionError("Cannot resolve canonical file for $changedPath", ex)
            }
            assert expected == actual
        }
    }

    protected abstract String resolveExpectedChange(File change)

    private static class TestCallback implements FileWatcherCallback {
        private AsyncConditions conditions
        private FileWatcherCallback delegateCallback

        AsyncConditions expectCallback(FileWatcherCallback delegateCallback) {
            this.conditions = new AsyncConditions()
            this.delegateCallback = delegateCallback
            return conditions
        }

        @Override
        void pathChanged(String path) {
            assert conditions != null
            println "> Changed: $path"
            conditions.evaluate {
                delegateCallback.pathChanged(path)
            }
            conditions = null
            delegateCallback = null
        }
    }

    protected abstract void waitForChangeEventLatency()
}
