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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OsxFileEventFunctions implements NativeIntegration {

    /**
     * Start watching the given directory hierarchies.
     *
     * <h3>Remarks:</h3>
     *
     * <ul>
     *     <li>Changes to any descendants to the given paths are reported.</li>
     *
     *     <li>Changes to the given paths themselves are not reported.</li>
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
    public FileWatcher startWatching(Collection<String> paths, long time, TimeUnit unit, FileWatcherCallback callback) {
        if (paths.isEmpty()) {
            return FileWatcher.EMPTY;
        }
        FunctionResult result = new FunctionResult();
        List<String> canonicalPaths = CanonicalPathUtil.canonicalizeAbsolutePaths(paths);
        FileWatcher watcher = startWatching(canonicalPaths.toArray(new String[0]), unit.toMillis(time), callback, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watching. Reason: " + result.getMessage());
        }
        return watcher;
    }

    private static native FileWatcher startWatching(String[] paths, long latencyInMillis, FileWatcherCallback callback, FunctionResult result);

    private static native void stopWatching(Object details, FunctionResult result);

    // Created from native code
    @SuppressWarnings("unused")
    private static class WatcherImpl implements FileWatcher {
        /**
         * Details is a Java object wrapper around whatever data the native implementation
         * needs to keep track of.
         */
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
                throw new NativeException("Failed to stop watching. Reason: " + result.getMessage());
            }
        }
    }
}
