package net.rubygrapefruit.platform;

/**
 * Functions to query and modify a process' state.
 *
 * Supported on Linux, OS X, Windows.
 */
public interface Process extends NativeIntegration {
    /**
     * Returns the process identifier.
     *
     * @throws NativeException On failure.
     */
    int getProcessId() throws NativeException;
}
