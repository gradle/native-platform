package net.rubygrapefruit.platform;

/**
 * Provides access to some system information.
 */
public interface SystemInfo extends NativeIntegration {
    /**
     * Returns the name of the kernel for the current operating system.
     *
     * @throws NativeException on failure.
     */
    String getKernelName() throws NativeException;

    /**
     * Returns the version of the kernel for the current operating system.
     *
     * @throws NativeException on failure.
     */
    String getKernelVersion() throws NativeException;

    /**
     * Returns the machine architecture, as reported by the operating system.
     *
     * @throws NativeException on failure.
     */
    String getMachineArchitecture() throws NativeException;
}
