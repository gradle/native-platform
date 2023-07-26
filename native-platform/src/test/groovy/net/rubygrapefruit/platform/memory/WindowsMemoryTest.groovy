/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.rubygrapefruit.platform.memory

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Requires
import spock.lang.Specification

@Requires({ Platform.current().windows })
class WindowsMemoryTest extends Specification {

    def "caches memory instance"() {
        expect:
        def memory = Native.get(WindowsMemory.class)
        memory.is(Native.get(WindowsMemory.class))
    }

    def "can query Windows memory info"() {
        given:
        def memory = Native.get(WindowsMemory.class)

        when:
        def memoryInfo = memory.memoryInfo
        def totalPhysicalMemory = MemoryTest.jmxTotalPhysicalMemory
        def availablePhysicalMemory = MemoryTest.jmxAvailablePhysicalMemory

        then:
        memoryInfo.totalPhysicalMemory > 0
        memoryInfo.availablePhysicalMemory > 0
        memoryInfo.availablePhysicalMemory <= memoryInfo.totalPhysicalMemory
        memoryInfo.commitTotal > 0
        memoryInfo.commitLimit > memoryInfo.commitTotal
        // Windows commitLimit must be greater than the physical memory, since it is that plus the page files
        memoryInfo.commitLimit >= memoryInfo.totalPhysicalMemory
        memoryInfo.totalPhysicalMemory == totalPhysicalMemory
        // These measurements should be close to each other, possibly inequal due to system memory churn
        Math.abs(memoryInfo.availablePhysicalMemory - availablePhysicalMemory) < 1024 * 1024
    }
}
