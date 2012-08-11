package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.SystemInfo;

public class MutableSystemInfo implements SystemInfo {
    public String osName;
    public String osVersion;
    public String characterEncoding;
    public String machineArchitecture;

    @Override
    public String getKernelName() {
        return osName;
    }

    @Override
    public String getKernelVersion() {
        return osVersion;
    }

    @Override
    public String getMachineArchitecture() {
        return machineArchitecture;
    }
}
