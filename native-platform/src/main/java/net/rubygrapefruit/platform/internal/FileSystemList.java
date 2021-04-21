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

import net.rubygrapefruit.platform.file.CaseSensitivity;
import net.rubygrapefruit.platform.file.FileSystemInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileSystemList {
    public final List<FileSystemInfo> fileSystems = new ArrayList<FileSystemInfo>();

    public void add(String mountPoint, String fileSystemType, String deviceName, boolean remote, boolean caseSensitive, boolean casePreserving) {
        fileSystems.add(new DefaultFileSystemInfo(new File(mountPoint), fileSystemType, deviceName, remote, new DefaultCaseSensitivity(caseSensitive, casePreserving)));
    }

    public void addForUnknownCaseSensitivity(String mountPoint, @Nullable String fileSystemType, String deviceName, boolean remote) {
        fileSystems.add(new DefaultFileSystemInfo(new File(mountPoint), fileSystemType == null ? "unknown" : fileSystemType, deviceName, remote, null));
    }

    private static class DefaultCaseSensitivity implements CaseSensitivity {
        private final boolean caseSensitive;
        private final boolean casePreserving;

        public DefaultCaseSensitivity(boolean caseSensitive, boolean casePreserving) {
            this.caseSensitive = caseSensitive;
            this.casePreserving = casePreserving;
        }

        @Override
        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        @Override
        public boolean isCasePreserving() {
            return casePreserving;
        }

        @Override
        public String toString() {
            return "CaseSensitivity{" +
                "caseSensitive=" + caseSensitive +
                ", casePreserving=" + casePreserving +
                '}';
        }
    }
}
