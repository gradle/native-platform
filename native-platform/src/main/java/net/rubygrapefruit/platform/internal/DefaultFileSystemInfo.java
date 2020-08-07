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

import net.rubygrapefruit.platform.file.FileSystemInfo;

import java.io.File;

public class DefaultFileSystemInfo implements FileSystemInfo {
    private final File mountPoint;
    private final String fileSystemType;
    private final String deviceName;
    private final boolean remote;
    private final boolean caseSensitive;
    private final boolean casePreserving;

    public DefaultFileSystemInfo(File mountPoint, String fileSystemType, String deviceName, boolean remote,
                                 boolean caseSensitive, boolean casePreserving) {
        this.mountPoint = mountPoint;
        this.fileSystemType = fileSystemType;
        this.deviceName = deviceName;
        this.remote = remote;
        this.caseSensitive = caseSensitive;
        this.casePreserving = casePreserving;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public File getMountPoint() {
        return mountPoint;
    }

    public String getFileSystemType() {
        return fileSystemType;
    }

    public boolean isRemote() {
        return remote;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isCasePreserving() {
        return casePreserving;
    }
}
