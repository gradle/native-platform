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
import net.rubygrapefruit.platform.SystemInfo;

public class MutableSystemInfo implements SystemInfo {
    // Fields set from native code
    public String osName;
    public String osVersion;
    public String machineArchitecture;
    public String hostname;

    public String getKernelName() {
        return osName;
    }

    public String getKernelVersion() {
        return osVersion;
    }

    public String getArchitectureName() {
        if (osName.startsWith("Darwin")) {
            // On macOS, the architecture field contains the CPU name, not the architecture
            if (machineArchitecture.startsWith("Apple")) {
                return "arm64";
            }
            if (machineArchitecture.startsWith("Intel")) {
                return "x86_64";
            }
        }
        return machineArchitecture;
    }

    public String getHostname() {
        return hostname;
    }

    public Architecture getArchitecture() {
        String machineArchitecture = getArchitectureName();
        if (machineArchitecture.equals("amd64") || machineArchitecture.equals("x86_64")) {
            return Architecture.amd64;
        }
        if (machineArchitecture.equals("i386") || machineArchitecture.equals("x86") || machineArchitecture.equals("i686")) {
            return Architecture.i386;
        }
        if (machineArchitecture.equals("aarch64") || machineArchitecture.equals("arm64")) {
            return Architecture.aarch64;
        }
        if (machineArchitecture.equals("e2k")) {
            return Architecture.e2k;
        }
        throw new NativeException(String.format("Cannot determine architecture from kernel architecture name '%s'.", machineArchitecture));
    }

    // Called from native code
    void windows(int major, int minor, int build, boolean workstation, String arch, String host) {
        osName = toWindowsVersionName(major, minor, workstation);
        osVersion = String.format("%s.%s (build %s)", major, minor, build);
        machineArchitecture = arch;
        hostname = host;
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
                        return workstation ? "Windows XP Professional" : "Windows Server 2003";
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
                    case 3:
                        return workstation ? "Windows 8.1" : "Windows Server 2012 R2";
                }
                break;
            case 10:
                if (minor == 0) {
                    return workstation ? "Windows 10" : "Windows Server 2016";
                }
                break;
        }
        return "Windows (unknown version)";
    }
}
