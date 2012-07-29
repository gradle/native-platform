package net.rubygrapefruit.platform;

import java.io.File;

/**
 * Functions to query and modify a file's POSIX meta-data.
 */
public interface PosixFile extends NativeIntegration {
    void setMode(File path, int perms) throws NativeException;

    int getMode(File path) throws NativeException;
}
