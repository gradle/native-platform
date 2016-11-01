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

import net.rubygrapefruit.platform.DirEntry;
import net.rubygrapefruit.platform.FileInfo;

import java.util.ArrayList;
import java.util.List;

public class DirList {
    public List<DirEntry> files = new ArrayList<DirEntry>();

    public void addFile(String name, int type) {
        PosixDirEntry fileStat = new PosixDirEntry(name, FileInfo.Type.values()[type]);
        files.add(fileStat);
    }

    private class PosixDirEntry implements DirEntry {
        private final String name;
        private final Type type;

        PosixDirEntry(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public long getLastModifiedTime() {
            return 0;
        }

        public long getSize() {
            return 0;
        }
    }
}
