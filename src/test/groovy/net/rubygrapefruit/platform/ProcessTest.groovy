package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProcessTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final Process process = Native.get(Process.class)

    def "caches process instance"() {
        expect:
        Native.get(Process.class) == process
    }

    def "can get PID"() {
        expect:
        process.getProcessId() != 0
    }
}
