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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

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

    def "can open and close watcher on a directory receiving an event"() {
        given:
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChanges(dir)
        new File(dir, "a.txt").createNewFile()

        then:
        expectedChanges.await()
    }

    def "can open and close watcher on a directory receiving multiple events"() {
        given:
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChanges(dir)
        new File(dir, "a.txt").createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChanges(dir)
        waitForChangeEventLatency()
        new File(dir, "b.txt").createNewFile()

        then:
        expectedChanges.await()
    }

    def "can open and close watcher on multiple directories receiving multiple events"() {
        given:
        def dir2 = tmpDir.newFolder()

        startWatcher(dir, dir2)

        when:
        def expectedChanges = expectThat pathChanges(dir)
        new File(dir, "a.txt").createNewFile()

        then:
        expectedChanges.await()

        when:
        expectedChanges = expectThat pathChanges(dir2)
        new File(dir2, "b.txt").createNewFile()

        then:
        expectedChanges.await()
    }

    def "can be started once and stopped multiple times"() {
        given:
        startWatcher(dir)

        when:
        watcher.close()
        watcher.close()

        then:
        noExceptionThrown()
    }

    def "can be used multiple times"() {
        given:
        startWatcher(dir)

        when:
        def expectedChanges = expectThat pathChanges(dir)
        new File(dir, "a.txt").createNewFile()

        then:
        expectedChanges.await()
        stopWatcher()

        when:
        startWatcher(dir)
        expectedChanges = expectThat pathChanges(dir)
        new File(dir, "b.txt").createNewFile()

        then:
        expectedChanges.await()
    }

    protected abstract void startWatcher(File... roots)

    protected abstract void stopWatcher()

    private AsyncConditions expectThat(FileWatcherCallback delegateCallback) {
        return callback.expectCallback(delegateCallback)
    }

    private static FileWatcherCallback pathChanges(File path) {
        return { changedPath ->
            String expected = path.canonicalPath
            String actual
            try {
                actual = new File(changedPath).canonicalPath
            } catch (IOException ex) {
                throw new AssertionError("Cannot resolve canonical file for $changedPath", ex)
            }
            assert expected == actual
        }
    }

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
