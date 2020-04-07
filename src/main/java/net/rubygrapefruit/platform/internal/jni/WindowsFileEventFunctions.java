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
 *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#REMOVED REMOVED}
 *     events, Windows sometimes also reports a
 *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#MODIFIED MODIFIED}
 *     event for the same file. This can happen when deleting a file or renaming it.</li>
 *
 *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.
 *     Calling methods from the {@link FileWatcher} inside the callback method is undefined
 *     behavior and can lead to a deadlock.</li>
 ** </ul>
 */
// TODO What about symlinks?
// TODO What about SUBST drives?
public class WindowsFileEventFunctions extends AbstractFileEventFunctions {

    public static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    @Override
    public WatcherBuilder newWatcher(FileWatcherCallback callback) {
        return new WatcherBuilder(callback);
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder {
        private int bufferSize = DEFAULT_BUFFER_SIZE;

        private WatcherBuilder(FileWatcherCallback callback) {
            super(callback);
        }

        /**
         * Set the buffer size used to collect events.
         * Default value is {@value DEFAULT_BUFFER_SIZE} bytes.
         */
        public WatcherBuilder withBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        @Override
        public FileWatcher start() {
            return startWatcher0(bufferSize, new NativeFileWatcherCallback(callback));
        }
    }

    private static native FileWatcher startWatcher0(int bufferSize, NativeFileWatcherCallback callback);
}
