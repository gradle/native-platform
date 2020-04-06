/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.rubygrapefruit.platform.internal.jni.fileevents;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.file.FileWatcher;

import java.io.File;
import java.util.Collection;

// Instantiated from native code
@SuppressWarnings("unused")
class NativeFileWatcher implements FileWatcher {
    /**
     * A Java object wrapper around the native server object.
     */
    private Object server;

    public NativeFileWatcher(Object server) {
        this.server = server;
    }

    @Override
    public void startWatching(Collection<File> paths) {
        if (server == null) {
            throw new IllegalStateException("Watcher already closed");
        }
        startWatching0(server, toAbsolutePaths(paths));
    }

    private native void startWatching0(Object server, String[] absolutePaths);

    @Override
    public boolean stopWatching(Collection<File> paths) {
        if (server == null) {
            throw new IllegalStateException("Watcher already closed");
        }
        return stopWatching0(server, toAbsolutePaths(paths));
    }

    private native boolean stopWatching0(Object server, String[] absolutePaths);

    private static String[] toAbsolutePaths(Collection<File> files) {
        String[] paths = new String[files.size()];
        int index = 0;
        for (File file : files) {
            paths[index++] = file.getAbsolutePath();
        }
        return paths;
    }

    @Override
    public void close() {
        if (server == null) {
            throw new NativeException("Closed already");
        }
        close0(server);
        server = null;
    }

    protected native void close0(Object details);
}
