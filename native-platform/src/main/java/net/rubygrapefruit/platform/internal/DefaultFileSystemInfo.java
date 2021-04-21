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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.file.CaseSensitivity;
import net.rubygrapefruit.platform.file.FileSystemInfo;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultFileSystemInfo implements FileSystemInfo {
    private final File mountPoint;
    private final String fileSystemType;
    private final String deviceName;
    private final boolean remote;
    private final CaseSensitivity caseSensitivity;

    public DefaultFileSystemInfo(File mountPoint, String fileSystemType, String deviceName, boolean remote, @Nullable CaseSensitivity caseSensitivity) {
        this.mountPoint = mountPoint;
        this.fileSystemType = fileSystemType;
        this.deviceName = deviceName;
        this.remote = remote;
        this.caseSensitivity = caseSensitivity;
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

    @Nullable
    @Override
    public CaseSensitivity getCaseSensitivity() {
        return caseSensitivity;
    }

    public boolean isRemote() {
        return remote;
    }

    public boolean isCaseSensitive() {
        return getCaseSensitivityOrThrow().isCaseSensitive();
    }

    public boolean isCasePreserving() {
        return getCaseSensitivityOrThrow().isCasePreserving();
    }

    private CaseSensitivity getCaseSensitivityOrThrow() {
        if (caseSensitivity == null) {
            throw new NativeException("Could get file system attributes for file system at " + mountPoint);
        }
        return caseSensitivity;
    }

    @Override
    public String toString() {
        return "FileSystemInfo{" +
            "mountPoint=" + mountPoint +
            ", fileSystemType='" + fileSystemType + '\'' +
            ", deviceName='" + deviceName + '\'' +
            ", remote=" + remote +
            ", caseSensitivity=" + caseSensitivity +
            '}';
    }
}
