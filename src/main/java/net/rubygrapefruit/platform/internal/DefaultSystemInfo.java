package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

public class DefaultSystemInfo implements SystemInfo {
    MutableSystemInfo systemInfo = new MutableSystemInfo();

    public DefaultSystemInfo() {
        FunctionResult result = new FunctionResult();
        NativeLibraryFunctions.getSystemInfo(systemInfo, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not fetch system information: %s",
                    result.getMessage()));
        }
    }

    @Override
    public String getKernelName() {
        return systemInfo.getKernelName();
    }

    @Override
    public String getKernelVersion() {
        return systemInfo.getKernelVersion();
    }

    @Override
    public String getMachineArchitecture() {
        return systemInfo.getMachineArchitecture();
    }

    public String getCharacterEncoding() {
        return systemInfo.characterEncoding;
    }
}
