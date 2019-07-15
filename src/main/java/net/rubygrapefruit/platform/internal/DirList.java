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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.file.DirEntry;
import net.rubygrapefruit.platform.file.FileInfo;

import java.util.ArrayList;
import java.util.List;

public class DirList {
    public List<DirEntry> files = new ArrayList<DirEntry>();

    // Called from native code
    @SuppressWarnings("UnusedDeclaration")
    public void addFile(String name, int type, long size, long lastModified) {
        DefaultDirEntry fileStat = new DefaultDirEntry(name, FileInfo.Type.values()[type], size, lastModified);
        files.add(fileStat);
    }

    private static class DefaultDirEntry implements DirEntry {
        private final String name;
        private final Type type;
        private final long size;
        private final long lastModified;

        DefaultDirEntry(String name, Type type, long size, long lastModified) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.lastModified = lastModified;
        }

        @Override
        public String toString() {
            return name;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public long getLastModifiedTime() {
            return lastModified;
        }

        public long getSize() {
            return size;
        }
    }
}
