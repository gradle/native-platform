package net.rubygrapefruit.platform;

/**
 * Functions to query and modify a process' state.
 */
@ThreadSafe
public interface Process extends NativeIntegration {
    /**
     * Returns the process identifier.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    int getProcessId() throws NativeException;
}
