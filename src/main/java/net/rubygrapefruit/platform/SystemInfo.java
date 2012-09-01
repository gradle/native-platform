package net.rubygrapefruit.platform;

/**
 * Provides access to some system information. This is a snapshot view and does not change.
 */
public interface SystemInfo extends NativeIntegration {
    /**
     * Returns the name of the kernel for the current operating system.
     */
    String getKernelName();

    /**
     * Returns the version of the kernel for the current operating system.
     */
    String getKernelVersion();

    /**
     * Returns the machine architecture, as reported by the operating system.
     */
    String getMachineArchitecture();
}
