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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;

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
public class LinuxFileEventFunctions extends AbstractNativeFileEventFunctions<LinuxFileEventFunctions.LinuxFileWatcher> {

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

    public static class LinuxFileWatcher extends NativeFileWatcher {
        public LinuxFileWatcher(Object server, NativeFileWatcherCallback callback) {
            super(server, callback);
        }

        /**
         * Stops watching any directories that have been moved to a different path since registration,
         * and returns the list of the registered paths that have been dropped.
         */
        public List<File> stopWatchingMovedPaths(Collection<File> pathsToCheck) {
            String[] absolutePathsToCheck = toAbsolutePaths(pathsToCheck);
            List<String> droppedPathStrings = new ArrayList<String>();
            stopWatchingMovedPaths0(server, absolutePathsToCheck, droppedPathStrings);
            List<File> droppedPaths = new ArrayList<File>(droppedPathStrings.size());
            for (String droppedPath : droppedPathStrings) {
                droppedPaths.add(new File(droppedPath));
            }
            return droppedPaths;
        }

        private native void stopWatchingMovedPaths0(Object server, String[] absolutePathsToCheck, List<String> droppedPaths);
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder<LinuxFileWatcher> {
        WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue);
        }

        @Override
        protected LinuxFileWatcher createWatcher(NativeFileWatcherCallback callback) {
            Object server = startWatcher0(callback);
            return new LinuxFileWatcher(server, callback);
        }
    }

    private static native Object startWatcher0(NativeFileWatcherCallback callback);
}
