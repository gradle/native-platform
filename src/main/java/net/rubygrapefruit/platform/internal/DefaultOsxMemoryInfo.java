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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.memory.OsxMemoryInfo;

public class DefaultOsxMemoryInfo implements OsxMemoryInfo {
    private long pageSize;
    private long freeCount;
    private long inactiveCount;
    private long wiredCount;
    private long activeCount;
    private long externalCount;
    private long speculativeCount;

    private long totalMem;
    private long availableMem;

    public void details(long pageSize,
                        long freeCount,
                        long inactiveCount,
                        long wiredCount,
                        long activeCount,
                        long externalCount,
                        long speculativeCount,
                        long totalMem,
                        long availableMem) {
        this.pageSize = pageSize;
        this.freeCount = freeCount;
        this.inactiveCount = inactiveCount;
        this.wiredCount = wiredCount;
        this.activeCount = activeCount;
        this.externalCount = externalCount;
        this.speculativeCount = speculativeCount;
        this.totalMem = totalMem;
        this.availableMem = availableMem;
    }

    public long getPageSize() {
        return pageSize;
    }

    public long getFreePagesCount() {
        return freeCount;
    }

    public long getInactivePagesCount() {
        return inactiveCount;
    }

    public long getWiredPagesCount() {
        return wiredCount;
    }

    public long getActivePagesCount() {
        return activeCount;
    }

    public long getExternalPagesCount() {
        return externalCount;
    }

    public long getSpeculativePagesCount() {
        return speculativeCount;
    }

    public long getTotalPhysicalMemory() {
        return totalMem;
    }

    public long getAvailablePhysicalMemory() {
        return availableMem;
    }
}
