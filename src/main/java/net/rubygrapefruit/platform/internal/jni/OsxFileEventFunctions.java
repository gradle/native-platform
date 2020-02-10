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

package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;

import java.util.concurrent.TimeUnit;

public class OsxFileEventFunctions extends AbstractFileEventFunctions {

    /**
     * Start watching the given directory hierarchies.
     *
     * @param paths the absolute paths to watch.
     * @param latency throttle / coalesce events for the given amount of time, {@code 0} meaning no coalescing.
     * @param unit the time unit for {@code latency}.
     * @param callback the callback to invoke when changes are detected.
     *
     * <h3>Remarks:</h3>
     *
     * <ul>
     *     <li>Changes to the given paths themselves are reported.</li>
     *
     *     <li>Changes to any descendants to the given paths are reported.</li>
     *
     *     <li>Changes are reported as <em>canonical</em> paths. This means:
     *     <ul>
     *         <li>When watching a path with a different case, the canonical one is used to report changes.</li>
     *         <li>Symlinks are resolved and changes are reported against the resolved path.</li>
     *     </ul>
     *     </li>
     *
     *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.</li>
     *
     *     <li>Removals are reported as a single
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#REMOVED REMOVED} event.</li>
     *
     *     <li>Renames are reported as the source file being
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#REMOVED REMOVED}
     *     and the target being
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#CREATED CREATED}.</li>
     *
     *     <li>Exceptions happening in the callback are currently silently ignored.</li>
     * </ul>
     */
    // TODO How to set kFSEventStreamCreateFlagNoDefer when latency is non-zero?
    public FileWatcher startWatcher(long latency, TimeUnit unit, FileWatcherCallback callback) {
        return startWatcher(unit.toMillis(latency), new NativeFileWatcherCallback(callback));
    }

    private static native FileWatcher startWatcher(long latencyInMillis, NativeFileWatcherCallback callback);
}
