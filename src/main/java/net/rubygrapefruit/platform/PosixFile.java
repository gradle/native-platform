package net.rubygrapefruit.platform;

import java.io.File;

/**
 * Functions to query and modify a file's POSIX meta-data.
 */
@ThreadSafe
public interface PosixFile extends NativeIntegration {
    /**
     * Sets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void setMode(File path, int perms) throws NativeException;

    /**
     * Gets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    int getMode(File path) throws NativeException;

    /**
     * Creates a symbolic link.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void symlink(File link, String contents) throws NativeException;

    /**
     * Reads the contents of a symbolic link.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    String readLink(File link) throws NativeException;
}
