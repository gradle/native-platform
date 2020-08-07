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

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.lang.management.ManagementFactory

@IgnoreIf({ !Platform.current().macOs })
class MemoryTest extends Specification {
    def "caches memory instance"() {
        expect:
        def memory = Native.get(Memory.class)
        memory.is(Native.get(Memory.class))
    }

    def "can query system memory"() {
        expect:
        def memory = Native.get(Memory.class)

        def memoryInfo = memory.memoryInfo
        memoryInfo.totalPhysicalMemory > 0
        memoryInfo.totalPhysicalMemory == jmxTotalPhysicalMemory
        memoryInfo.availablePhysicalMemory > 0
        memoryInfo.availablePhysicalMemory <= memoryInfo.totalPhysicalMemory
    }

    long getJmxTotalPhysicalMemory() {
        ManagementFactory.operatingSystemMXBean.totalPhysicalMemorySize
    }
}
