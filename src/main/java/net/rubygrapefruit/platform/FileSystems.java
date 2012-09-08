package net.rubygrapefruit.platform;

import java.util.List;

/**
 * Provides access to the file systems of the current machine.
 */
@ThreadSafe
public interface FileSystems extends NativeIntegration {
    /**
     * Returns the set of all file systems for the current machine.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    List<FileSystem> getFileSystems() throws NativeException;
}
