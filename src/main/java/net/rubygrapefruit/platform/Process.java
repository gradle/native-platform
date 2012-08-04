package net.rubygrapefruit.platform;

/**
 * Functions to query and modify a process' meta-data
 */
public interface Process extends NativeIntegration {
    int getProcessId() throws NativeException;
}
