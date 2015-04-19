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

package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@Timeout(20)
class FileEventsTest extends Specification {
    @Rule
    TemporaryFolder tmpDir
    final FileEvents fileEvents = Native.get(FileEvents.class)

    def "caches file events instance"() {
        expect:
        Native.get(FileEvents.class) == fileEvents
    }

    def "can open and close watch on a directory without receiving any events"() {
        given:
        def dir = tmpDir.newFolder()

        expect:
        def watch = fileEvents.startWatch(dir)
        watch.close()
    }

    def "can close watch multiple times"() {
        given:
        def dir = tmpDir.newFolder()

        expect:
        def watch = fileEvents.startWatch(dir)
        watch.close()
        watch.close()
        watch.close()
    }

    def "cannot receive events after close"() {
        given:
        def dir = tmpDir.newFolder()
        def watch = fileEvents.startWatch(dir)
        watch.close()

        when:
        watch.nextChange()

        then:
        ResourceClosedException e = thrown()
        e.message == 'This file watch has been closed.'
    }

    def "thread blocked waiting for event receives closed exception on close"() {
        given:
        def dir = tmpDir.newFolder()
        def watch = watcher(dir)

        when:
        watch.close()

        then:
        watch.watchThreadReceivedCloseException()
    }

    def "can watch for creation and removal of files in directory"() {
        given:
        def dir = tmpDir.newFolder()
        def child1 = new File(dir, "child")
        def child2 = new File(dir, "child\u0301")
        def watch = watcher(dir)

        when:
        child1.text = "hi"

        then:
        watch.changed()

        when:
        child2.withOutputStream {}

        then:
        watch.changed()

        when:
        child2.delete()

        then:
        watch.changed()

        cleanup:
        watch.close()
    }

    def "can watch for creation and removal of directories in directory"() {
        given:
        def dir = tmpDir.newFolder()
        def child1 = new File(dir, "child")
        def child2 = new File(dir, "child\u0301")
        def watch = watcher(dir)

        when:
        child1.mkdir()

        then:
        watch.changed()

        when:
        child2.mkdir()

        then:
        watch.changed()

        when:
        child2.delete()

        then:
        watch.changed()

        cleanup:
        watch.close()
    }

    def watcher(File target) {
        return new Watcher(fileEvents.startWatch(target))
    }

    static class Watcher {
        final FileWatch watch
        final Thread watcher
        final BlockingQueue<Object> queue = new LinkedBlockingDeque<>()

        Watcher(FileWatch watch) {
            this.watch = watch
            watcher = new Thread() {
                @Override
                void run() {
                    try {
                        while (true) {
                            println "waiting for change"
                            watch.nextChange()
                            println "received"
                            queue.add(true)
                        }
                    } catch (Throwable t) {
                        queue.add(t)
                    }
                }
            }
            watcher.start()
        }

        void changed() {
            def result = queue.poll(5, TimeUnit.SECONDS)
            if (result == null) {
                throw new AssertionError("Timeout waiting for change event to be received.")
            }
        }

        void watchThreadReceivedCloseException() {
            assert queue.size() == 1
            assert queue.first() instanceof ResourceClosedException
        }

        void close() {
            watch.close()
            watcher.join()
            assert queue.size() > 0
            assert queue.last() instanceof ResourceClosedException
            for (Object o : queue) {
                if ((o instanceof Throwable) && !(o instanceof ResourceClosedException)) {
                    throw o
                }
            }
        }
    }
}
