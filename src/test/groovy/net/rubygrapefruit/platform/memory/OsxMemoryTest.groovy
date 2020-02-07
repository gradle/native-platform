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
import net.rubygrapefruit.platform.internal.DefaultOsxMemoryInfo
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.management.ManagementFactory
import java.util.regex.Matcher
import java.util.regex.Pattern

@IgnoreIf({ !Platform.current().macOs })
class OsxMemoryTest extends Specification {

    def "caches memory instance"() {
        expect:
        def memory = Native.get(OsxMemory.class)
        memory.is(Native.get(OsxMemory.class))
    }

    def "can query OSX memory info"() {
        given:
        def memory = Native.get(OsxMemory.class)

        when:
        def vmStatInfo = getFromVmStatCommand()
        def memoryInfo = memory.memoryInfo

        and:
        def csv = toCsv(memoryInfo, vmStatInfo)
        println csv

        then:
        memoryInfo.totalPhysicalMemory > 0
        memoryInfo.availablePhysicalMemory > 0
        memoryInfo.availablePhysicalMemory <= memoryInfo.totalPhysicalMemory
        memoryInfo.totalPhysicalMemory == jmxTotalPhysicalMemory
        memoryInfo.availablePhysicalMemory > jmxAvailablePhysicalMemory
    }

    @Ignore
    def "indefinitely sample OSX memory info"() {
        given:
        def memory = Native.get(OsxMemory.class)
        def conditions = new PollingConditions(timeout: Double.MAX_VALUE, initialDelay: 5, delay: 5)

        expect:
        conditions.eventually {
            def vmStatInfo = getFromVmStatCommand()
            def memoryInfo = memory.memoryInfo

            def csv = toCsv(memoryInfo, vmStatInfo)
            println csv
            new File("/tmp/osx-memory-sampling-${System.currentTimeMillis()}.csv").text = csv

            assert false
        }
    }

    def toCsv(OsxMemoryInfo hostStat, OsxMemoryInfo vmStat) {
        assert hostStat.pageSize == vmStat.pageSize
        def pageSize = hostStat.pageSize
        def mb = { long pages -> (int) pageSize * pages / 1024 / 1024
        }
        def row = { String title, long nativePages, long vmStatPages -> "$title,$nativePages,${mb nativePages},${mb vmStatPages},$vmStatPages\n"
        }
        def csv = "value,host_statistics_pages,host_statistics_mb,vm_stat_mb,vm_stat_pages\n"
        csv += row 'free', hostStat.freePagesCount, vmStat.freePagesCount
        csv += row 'inactive', hostStat.inactivePagesCount, vmStat.inactivePagesCount
        csv += row 'wired', hostStat.wiredPagesCount, vmStat.wiredPagesCount
        csv += row 'active', hostStat.activePagesCount, vmStat.activePagesCount
        csv += row 'external', hostStat.externalPagesCount, vmStat.externalPagesCount
        csv += row 'speculative', hostStat.speculativePagesCount, vmStat.speculativePagesCount
        csv += row 'total',
                (long) (hostStat.totalPhysicalMemory / pageSize),
                (long) (vmStat.totalPhysicalMemory / pageSize)
        csv += row 'available_fcache',
                hostStat.freePagesCount + hostStat.externalPagesCount - hostStat.speculativePagesCount,
                vmStat.freePagesCount + vmStat.externalPagesCount - vmStat.speculativePagesCount
        csv += row 'available_inact',
                hostStat.freePagesCount + hostStat.inactivePagesCount - hostStat.speculativePagesCount,
                vmStat.freePagesCount + vmStat.inactivePagesCount - vmStat.speculativePagesCount
        return csv
    }

    long getJmxTotalPhysicalMemory() {
        ManagementFactory.operatingSystemMXBean.totalPhysicalMemorySize
    }

    long getJmxAvailablePhysicalMemory() {
        ManagementFactory.operatingSystemMXBean.freePhysicalMemorySize
    }

    def VMSTAT_LINE_PATTERN = Pattern.compile(/^\D+(\d+)\D+$/)
    def vmstatMatcher = VMSTAT_LINE_PATTERN.matcher("")

    OsxMemoryInfo getFromVmStatCommand() {
        def output = "vm_stat".execute().text
        def lines = output.readLines()
        long pageSize = parseVmstatPages lines.get(0)
        long freeCount = 0
        long inactiveCount = 0
        long wiredCount = 0
        long activeCount = 0
        long externalCount = 0
        long speculativeCount = 0
        lines.drop(1).each { line ->
            if (line.startsWith("Pages free")) {
                freeCount += parseVmstatPages line
            } else if (line.startsWith("Pages inactive")) {
                inactiveCount = parseVmstatPages line
            } else if (line.startsWith("Pages wired")) {
                wiredCount = parseVmstatPages line
            } else if (line.startsWith("Pages active")) {
                activeCount = parseVmstatPages line
            } else if (line.startsWith("File-backed pages")) {
                externalCount = parseVmstatPages line
            } else if (line.startsWith("Pages speculative")) {
                speculativeCount = parseVmstatPages line
                freeCount += speculativeCount
            }
        }
        long totalMem = jmxTotalPhysicalMemory
        long availableMem = (freeCount + externalCount) * pageSize
        DefaultOsxMemoryInfo info = new DefaultOsxMemoryInfo()
        info.details(pageSize,
                freeCount, inactiveCount,
                wiredCount, activeCount,
                externalCount,
                speculativeCount,
                totalMem,
                availableMem)
        return info
    }

    private long parseVmstatPages(String line) {
        Matcher matcher = vmstatMatcher.reset line
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1))
        }
        throw new UnsupportedOperationException("Unable to parse vm_stat output")
    }
}
