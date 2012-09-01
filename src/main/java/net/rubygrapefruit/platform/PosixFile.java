package net.rubygrapefruit.platform;

import java.io.File;

/**
 * Functions to query and modify a file's POSIX meta-data.
 */
public interface PosixFile extends NativeIntegration {
    /**
     * Sets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    void setMode(File path, int perms) throws NativeException;

    /**
     * Gets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    int getMode(File path) throws NativeException;

    /**
     * Creates a symbolic link.
     *
     * @throws NativeException On failure.
     */
    void symlink(File link, String contents) throws NativeException;

    /**
     * Reads the contents of a symbolic link.
     *
     * @throws NativeException On failure.
     */
    String readLink(File link) throws NativeException;
}
