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

import net.rubygrapefruit.platform.file.PosixFileInfo;

public class FileStat implements PosixFileInfo {
    private final String path;
    private int mode;
    private Type type;
    private int uid;
    private int gid;
    private long size;
    private long modificationTime;
    private long blockSize;

    public FileStat(String path) {
        this.path = path;
    }

    public void details(int type, int mode, int uid, int gid, long size, long modificationTime, int blockSize) {
        this.type = Type.values()[type];
        this.mode = mode;
        this.uid = uid;
        this.gid = gid;
        this.size = size;
        this.modificationTime = modificationTime;
        this.blockSize = blockSize;
    }

    @Override
    public String toString() {
        return path;
    }

    public int getMode() {
        return mode;
    }

    public Type getType() {
        return type;
    }

    public int getUid() {
        return uid;
    }

    public int getGid() {
        return gid;
    }

    public long getSize() {
        return size;
    }

    public long getBlockSize() {
        return blockSize;
    }

    public long getLastModifiedTime() {
        return modificationTime;
    }
}
