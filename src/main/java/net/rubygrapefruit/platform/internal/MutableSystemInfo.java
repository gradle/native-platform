package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.SystemInfo;

public class MutableSystemInfo implements SystemInfo {
    public String osName;
    public String osVersion;
    public String characterEncoding;
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
}
