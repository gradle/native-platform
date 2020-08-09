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

import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File watcher for macOS. Reports changes to the watched paths and any of their descendants.
 *
 * <h3>Remarks:</h3>
 *
 * <ul>
 *     <li>Changes are reported as <em>non-canonical</em> paths. This means:
 *     <ul>
 *         <li>When watching a path with a different case, the reported case will match
 *         the one used in starting the watcher.</li>
 *         <li>Symlinks are not canonicalized, and changes are reported against the watched path.</li>
 *     </ul>
 *     </li>
 *
 *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.
 *     Calling methods from the {@link FileWatcher} inside the callback method is undefined
 *     behavior and can lead to a deadlock.</li>
 * </ul>
 */
public class OsxFileEventFunctions extends AbstractFileEventFunctions {
    private static final long DEFAULT_LATENCY_IN_MS = 0;

    @Override
    public WatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        return new WatcherBuilder(eventQueue);
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder {
        private long latencyInMillis = DEFAULT_LATENCY_IN_MS;

        WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue);
        }

        /**
         * Set the latency for handling events.
         * The default is {@value DEFAULT_LATENCY_IN_MS} ms.
         *
         * @param latency coalesce events for the given amount of time, {@code 0} meaning no coalescing.
         * @param unit the time unit for {@code latency}.
         */
        public WatcherBuilder withLatency(long latency, TimeUnit unit) {
            latencyInMillis = unit.toMillis(latency);
            return this;
        }

        @Override
        protected Object startWatcher(NativeFileWatcherCallback callback) {
            return startWatcher0(latencyInMillis, callback);
        }
    }

    private static native Object startWatcher0(long latencyInMillis, NativeFileWatcherCallback callback);
}
