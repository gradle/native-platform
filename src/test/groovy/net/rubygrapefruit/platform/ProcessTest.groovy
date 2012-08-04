package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProcessTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final Process process = Native.get(Process.class)

    def "can get PID"() {
        expect:
        process.getPid() != 0
    }
}
