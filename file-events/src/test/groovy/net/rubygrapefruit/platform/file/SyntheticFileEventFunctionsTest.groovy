package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.internal.jni.TestFileEventFunctions
import spock.lang.Specification

import java.util.concurrent.LinkedBlockingDeque

class SyntheticFileEventFunctionsTest extends Specification {
    def "termination produces termination event"() {
        def service = new TestFileEventFunctions()
        def eventQueue = new LinkedBlockingDeque()

        def watcher = service
            .newWatcher(eventQueue)
            .start()

        expect:
        watcher != null
        eventQueue.empty

        when:
        watcher.shutdown()
        then:
        eventQueue.toList()*.toString() == ["TERMINATE"]
    }
}
