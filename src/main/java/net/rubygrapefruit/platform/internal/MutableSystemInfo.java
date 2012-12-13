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

import net.rubygrapefruit.platform.SystemInfo;

public class MutableSystemInfo implements SystemInfo {
    // Fields set from native code
    public String osName;
    public String osVersion;
    public String machineArchitecture;

    public String getKernelName() {
        return osName;
    }

    public String getKernelVersion() {
        return osVersion;
    }

    public String getMachineArchitecture() {
        return machineArchitecture;
    }

    // Called from native code
    void windows(int major, int minor, int build, boolean workstation, String arch) {
        osName = toWindowsVersionName(major, minor, workstation);
        osVersion = String.format("%s.%s (build %s)", major, minor, build);
        machineArchitecture = arch;
    }

    private String toWindowsVersionName(int major, int minor, boolean workstation) {
        switch (major) {
            case 5:
                switch (minor) {
                    case 0:
                        return "Windows 2000";
                    case 1:
                        return "Windows XP";
                    case 2:
                        return "Windows Server 2003";
                }
                break;
            case 6:
                switch (minor) {
                    case 0:
                        return workstation ? "Windows Vista" : "Windows Server 2008";
                    case 1:
                        return workstation ? "Windows 7" : "Windows Server 2008 R2";
                    case 2:
                        return workstation ? "Windows 8" : "Windows Server 2012";
                }
                break;
        }
        return "Windows (unknown version)";
    }
}
