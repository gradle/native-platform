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

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File watcher for Windows. Reports changes to the watched paths and any of their descendants.
 *
 * <h3>Remarks:</h3>
 *
 * <ul>
 *     <li>Changes are reported as <em>canonical</em> paths. When watching a path with a
 *     different case, the canonical one is used to report changes.</li>
 *
 *     <li>When reporting
 *     {@link net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType#REMOVED REMOVED}
 *     events, Windows sometimes also reports a
 *     {@link net.rubygrapefruit.platform.file.FileWatchEvent.ChangeType#MODIFIED MODIFIED}
 *     event for the same file. This can happen when deleting a file or renaming it.</li>
 *
 *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.
 *     Calling methods from the {@link FileWatcher} inside the callback method is undefined
 *     behavior and can lead to a deadlock.</li>
 * </ul>
 */
public class WindowsFileEventFunctions extends AbstractFileEventFunctions {

    public static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
    public static final int DEFAULT_COMMAND_TIMEOUT_IN_SECONDS = 5;

    @Override
    public WatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        return new WatcherBuilder(eventQueue);
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder {
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private long commandTimeoutInMillis = TimeUnit.SECONDS.toMillis(DEFAULT_COMMAND_TIMEOUT_IN_SECONDS);

        private WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue);
        }

        /**
         * Set the buffer size used to collect events.
         * Default value is {@value DEFAULT_BUFFER_SIZE} bytes.
         */
        public WatcherBuilder withBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Sets the timeout for commands to get scheduled on the run loop.
         *
         * Commands are {@link FileWatcher#startWatching(Collection)},
         * {@link FileWatcher#stopWatching(Collection)} and {@link FileWatcher#shutdown()},
         * The Windows file watcher relies on scheduling the execution of these commands
         * on the background thread.
         *
         * Defaults to {@value DEFAULT_COMMAND_TIMEOUT_IN_SECONDS} seconds.
         */
        public WatcherBuilder withCommandTimeout(int timeoutValue, TimeUnit timeoutUnit) {
            this.commandTimeoutInMillis = timeoutUnit.toMillis(timeoutValue);
            return this;
        }

        @Override
        protected Object startWatcher(NativeFileWatcherCallback callback) {
            return startWatcher0(bufferSize, commandTimeoutInMillis, callback);
        }
    }

    private static native Object startWatcher0(int bufferSize, long commandTimeoutInMillis, NativeFileWatcherCallback callback);
}
