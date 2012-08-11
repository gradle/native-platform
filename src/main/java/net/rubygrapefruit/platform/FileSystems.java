package net.rubygrapefruit.platform;

import java.util.List;

public interface FileSystems extends NativeIntegration {
    /**
     * Returns the set of all file systems for the current machine.
     *
     * @throws NativeException On failure.
     */
    List<FileSystem> getFileSystems() throws NativeException;
}
