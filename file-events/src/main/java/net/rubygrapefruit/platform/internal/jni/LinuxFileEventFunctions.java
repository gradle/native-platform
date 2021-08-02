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

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * File watcher for Linux. Reports changes to the watched paths and their immediate children.
 * Changes to deeper descendants are not reported.
 *
 * <h3>Remarks:</h3>
 *
 * <ul>
 *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.
 *     Calling methods from the {@link FileWatcher} inside the callback method is undefined
 *     behavior and can lead to a deadlock.</li>
 * </ul>
 */
public class LinuxFileEventFunctions extends AbstractFileEventFunctions<LinuxFileEventFunctions.LinuxFileWatcher> {

    public LinuxFileEventFunctions() {
        // We have seen some weird behavior on Alpine Linux that uses musl with Gradle that lead to crashes
        // As a band-aid we currently don't support file events on Linux with a non-glibc libc.
        // See also https://github.com/gradle/gradle/issues/17099
        if (!isGlibc0()) {
            throw new NativeIntegrationUnavailableException("File events on Linux are only supported with glibc");
        }
    }

    private static native boolean isGlibc0();

    @Override
    public WatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        return new WatcherBuilder(eventQueue);
    }

    public static class LinuxFileWatcher extends AbstractFileEventFunctions.NativeFileWatcher {
        public LinuxFileWatcher(Object server, long startTimeout, TimeUnit startTimeoutUnit, NativeFileWatcherCallback callback) throws InterruptedException {
            super(server, startTimeout, startTimeoutUnit, callback);
        }
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder<LinuxFileWatcher> {
        WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue);
        }

        @Override
        protected Object startWatcher(NativeFileWatcherCallback callback) throws InotifyInstanceLimitTooLowException {
            return startWatcher0(callback);
        }

        @Override
        protected LinuxFileWatcher createWatcher(Object server, long startTimeout, TimeUnit startTimeoutUnit, NativeFileWatcherCallback callback) throws InterruptedException {
            return new LinuxFileWatcher(server, startTimeout, startTimeoutUnit, callback);
        }
    }

    private static native Object startWatcher0(NativeFileWatcherCallback callback);
}
