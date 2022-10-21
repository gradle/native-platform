package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.internal.jni.TestFileEventFunctions
import spock.lang.Specification

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class SyntheticFileEventFunctionsTest extends Specification {
    def service = new TestFileEventFunctions()
    def eventQueue = new LinkedBlockingDeque()
    def watcher = service
        .newWatcher(eventQueue)
        .start()

    def "normal termination produces termination event"() {
        when:
        watcher.shutdown()
        watcher.awaitTermination(1, TimeUnit.SECONDS)
        then:
        eventQueue*.toString() == ["TERMINATE"]
    }

    def "failure produces failure event followed by termination events"() {
        when:
        watcher.failRunLoop()
        watcher.awaitTermination(1, TimeUnit.SECONDS)
        then:
        eventQueue*.toString() == ["FAILURE Error", "TERMINATE"]
    }
}
