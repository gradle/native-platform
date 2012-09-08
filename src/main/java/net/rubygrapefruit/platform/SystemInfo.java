package net.rubygrapefruit.platform;

/**
 * Provides access to some system information. This is a snapshot view and does not change.
 */
@ThreadSafe
public interface SystemInfo extends NativeIntegration {
    /**
     * Returns the name of the kernel for the current operating system.
     */
    @ThreadSafe
    String getKernelName();

    /**
     * Returns the version of the kernel for the current operating system.
     */
    @ThreadSafe
    String getKernelVersion();

    /**
     * Returns the machine architecture, as reported by the operating system.
     */
    @ThreadSafe
    String getMachineArchitecture();
}
