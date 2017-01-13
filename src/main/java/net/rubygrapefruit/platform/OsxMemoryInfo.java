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

package net.rubygrapefruit.platform;

/**
 * Detailed OSX memory info.
 *
 * <strong>This is not exactly what {@literal vm_stat} displays:</strong>
 *
 * {@literal vm_stat}'s {@literal Free pages}
 * is {@link #getFreePagesCount()} minus {@link #getSpeculativePagesCount()}.
 *
 * {@link #getExternalPagesCount()} is displayed as {@literal File-backed pages}.
 */
public interface OsxMemoryInfo {
    long getPageSize();

    long getFreePagesCount();

    long getInactivePagesCount();

    long getWiredPagesCount();

    long getActivePagesCount();

    long getExternalPagesCount();

    long getSpeculativePagesCount();

    long getTotalPhysicalMemory();

    /**
     * Calculated.
     */
    long getAvailablePhysicalMemory();
}
