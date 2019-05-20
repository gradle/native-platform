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

import net.rubygrapefruit.platform.file.FileInfo;
import net.rubygrapefruit.platform.file.WindowsFileInfo;

public class WindowsDirList extends DirList {
    // Called from native code
    @SuppressWarnings("UnusedDeclaration")
    @Override
    public void addFile(String name, int type, long size, long lastModified) {
        addFile(name, FileInfo.Type.values()[type], size, WindowsFileTime.toJavaTime(lastModified), 0, 0);
    }

    void addFile(String name, WindowsFileInfo fileInfo) {
        addFile(name, fileInfo.getType(), fileInfo.getSize(), fileInfo.getLastModifiedTime(), fileInfo.getVolumeId(), fileInfo.getFileId());
    }

    void addFile(String name, FileInfo.Type type, long size, long lastModified, int volumeId, long fileId) {
        if (volumeId == 0 && fileId == 0) {
            super.addFile(name, type, size, lastModified);
        } else {
            WindowsDirListEntry entry = new WindowsDirListEntry(name, type, size, lastModified, volumeId, fileId);
            addEntry(entry);
        }
    }

    protected static class WindowsDirListEntry extends DefaultDirEntry {
        private final int volumeId;
        private final long fileId;
        // Lazily initialized to avoid extra allocation if not needed
        private volatile WindowsFileKey key;

        WindowsDirListEntry(String name, Type type, long size, long lastModified, int volumeId, long fileId) {
            super(name, type, size, lastModified);
            this.volumeId = volumeId;
            this.fileId = fileId;
        }

        public Object getKey() {
            if (volumeId == 0 && fileId == 0) {
                return null;
            }
            if (key == null) {
                synchronized (this) {
                    if (key == null) {
                        key = new WindowsFileKey(volumeId, fileId);
                    }
                }
            }
            return key;
        }
    }
}
