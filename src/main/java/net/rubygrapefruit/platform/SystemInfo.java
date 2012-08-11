package net.rubygrapefruit.platform;

public interface SystemInfo extends NativeIntegration {
    String getKernelName();

    String getKernelVersion();

    String getMachineArchitecture();
}
