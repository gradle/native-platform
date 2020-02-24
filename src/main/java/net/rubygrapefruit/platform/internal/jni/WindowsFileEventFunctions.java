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

import java.util.Collection;

public class WindowsFileEventFunctions extends AbstractFileEventFunctions {

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
     *     </ul>
     *
     *     <li>Events arrive from a single background thread unique to the {@link FileWatcher}.</li>
     *
     *     <li>Removals are reported as a
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#MODIFIED MODIFIED} and a
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#REMOVED REMOVED} event.</li>
     *
     *     <li>Renames are reported as the source file being
     *     {@link net.rubygrapefruit.platform.file.FileWatcherCallback.Type#REMOVED REMOVED}.
     *     The creation of the target file is not reported.</li>
     *
     *     <li>Exceptions happening in the callback are currently silently ignored.</li>
     * </ul>
     */
    // TODO What about symlinks?
    // TODO What about SUBST drives?
    public FileWatcher startWatching(Collection<String> paths, FileWatcherCallback callback) {
        return createWatcher(paths, callback, new WatcherFactory() {
            @Override
            public FileWatcher createWatcher(String[] canonicalPaths, NativeFileWatcherCallback callback) {
                return startWatching(canonicalPaths, callback);
            }
        });
    }

    private static native FileWatcher startWatching(String[] paths, NativeFileWatcherCallback callback);

    // Created from native code
    @SuppressWarnings("unused")
    private static class WatcherImpl extends AbstractFileWatcher {
        public WatcherImpl(Object details) {
            super(details);
        }

        @Override
        protected native void startWatching(Object server, String absolutePath);

        @Override
        protected native void stopWatching(Object server, String absolutePath);

        @Override
        protected native void stop(Object details);
    }
}
