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

package net.rubygrapefruit.platform.memory

import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Requires

import java.lang.management.ManagementFactory

@Requires({ Platform.current().macOs || Platform.current().windows })
class MemoryTest extends NativePlatformSpec {
    static long getJmxTotalPhysicalMemory() {
        ManagementFactory.operatingSystemMXBean.totalPhysicalMemorySize
    }

    static long getJmxAvailablePhysicalMemory() {
        ManagementFactory.operatingSystemMXBean.freePhysicalMemorySize
    }

    def "caches memory instance"() {
        expect:
        def memory = getIntegration(Memory)
        memory.is(getIntegration(Memory))
    }

    def "can query system memory"() {
        expect:
        def memory = getIntegration(Memory)

        def memoryInfo = memory.memoryInfo
        memoryInfo.totalPhysicalMemory > 0
        memoryInfo.totalPhysicalMemory == jmxTotalPhysicalMemory
        memoryInfo.availablePhysicalMemory > 0
        memoryInfo.availablePhysicalMemory <= memoryInfo.totalPhysicalMemory
    }

    @Requires({ Platform.current().windows })
    def "memory instance is the OS-specific implementation on Windows"() {
        expect:
        getIntegration(Memory) instanceof WindowsMemory
    }

    @Requires({ Platform.current().macOs })
    def "memory instance is the OS-specific implementation on OSX"() {
        expect:
        getIntegration(Memory) instanceof OsxMemory
    }
}
