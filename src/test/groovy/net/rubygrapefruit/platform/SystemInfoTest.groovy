package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class SystemInfoTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final SystemInfo systemInfo = Native.get(SystemInfo.class)

    def "can query OS details"() {
        expect:
        systemInfo.kernelName
        systemInfo.kernelVersion
        systemInfo.machineArchitecture
    }
}
