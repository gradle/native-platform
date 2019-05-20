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

import net.rubygrapefruit.platform.file.WindowsFileInfo;

public class WindowsFileStat implements WindowsFileInfo {
    private final String path;
    private Type type;
    private long size;
    private long lastModified;
    private int volumeId;
    private long fileId;
    // Lazily initialized to avoid extra allocation if not needed
    private volatile WindowsFileKey key;

    public WindowsFileStat(String path) {
        this.path = path;
    }

    public void details(int type, long size, long lastModifiedWinTime) {
        this.type = Type.values()[type];
        this.size = size;
        this.lastModified = WindowsFileTime.toJavaTime(lastModifiedWinTime);
    }

    public void details(int type, long size, long lastModifiedWinTime, int volumeId, long fileId) {
        this.type = Type.values()[type];
        this.size = size;
        this.lastModified = this.type == Type.Missing ? 0 : WindowsFileTime.toJavaTime(lastModifiedWinTime);
        this.volumeId = volumeId;
        this.fileId = fileId;
    }

    @Override
    public String toString() {
        return path;
    }

    public Type getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public long getLastModifiedTime() {
        return lastModified;
    }

    public int getVolumeId() {
        return volumeId;
    }

    public long getFileId() {
        return fileId;
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
