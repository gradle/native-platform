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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.Collection;

public class WindowsFileEventFunctions implements NativeIntegration {

    public FileWatcher startWatching(Collection<String> paths, FileWatcherCallback callback) {
        if (paths.isEmpty()) {
            return FileWatcher.EMPTY;
        }
        FunctionResult result = new FunctionResult();
        FileWatcher watch = startWatching(paths.toArray(new String[0]), callback, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watch. Reason: " + result.getMessage());
        }
        return watch;
    }

    private static native FileWatcher startWatching(String[] paths, FileWatcherCallback callback, FunctionResult result);
    private static native void stopWatching(Object details, FunctionResult result);

    // Created from native code
    @SuppressWarnings("unused")
    private static class WatcherImpl implements FileWatcher {
        private Object details;

        public WatcherImpl(Object details) {
            this.details = details;
        }

        @Override
        public void close() {
            if (details == null) {
                return;
            }
            FunctionResult result = new FunctionResult();
            stopWatching(details, result);
            details = null;
            if (result.isFailed()) {
                throw new NativeException("Failed to stop watch. Reason: " + result.getMessage());
            }
        }
    }
}
