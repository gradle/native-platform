package net.rubygrapefruit.platform.internal

import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.SystemInfo

class MutableSystemInfoTest extends NativePlatformSpec {

    def "can get architecture on macOS"() {
        given:
        def systemInfo = new MutableSystemInfo()
        systemInfo.osName = "Darwin"

        when:
        systemInfo.machineArchitecture = processor

        then:
        systemInfo.architecture == expectedArchitecture

        where:
        processor                                        | expectedArchitecture
        // Officially supported macOS processors
        "Intel(R) Core(TM) i5-8500 CPU @ 3.00GHz"        | SystemInfo.Architecture.amd64
        "Intel(R) Core(TM) i5-10500 CPU @ 3.10GHz"       | SystemInfo.Architecture.amd64
        "Intel(R) Xeon(R) W-3223 CPU @ 3.50GHz"          | SystemInfo.Architecture.amd64

        // Newer Intel processors
        "11th Gen Intel(R) Core(TM) i7-11800H @ 2.30GHz" | SystemInfo.Architecture.amd64
        "12th Gen Intel(R) Core(TM) i7-12650HX"          | SystemInfo.Architecture.amd64
        "13th Gen Intel(R) Core(TM) i5-13600K"           | SystemInfo.Architecture.amd64
        "Intel(R) Core(TM) Ultra 7 255H"                 | SystemInfo.Architecture.amd64

        // AMD processors
        "AMD Ryzen 5 2600 Six-Core Processor"            | SystemInfo.Architecture.amd64
        "AMD Athlon(tm) II X2 215 Processor"             | SystemInfo.Architecture.amd64

        // Apple processors
        "Apple M1"                                       | SystemInfo.Architecture.aarch64
        "Apple M1 Pro"                                   | SystemInfo.Architecture.aarch64
        "Apple M2"                                       | SystemInfo.Architecture.aarch64
        "Apple M3"                                       | SystemInfo.Architecture.aarch64
        "Apple M4"                                       | SystemInfo.Architecture.aarch64
    }
}
