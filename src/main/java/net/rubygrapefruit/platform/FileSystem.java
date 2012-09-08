package net.rubygrapefruit.platform;

import java.io.File;

/**
 * Information about a file system. This is a snapshot view and does not change.
 */
@ThreadSafe
public interface FileSystem {
    /**
     * Returns the root directory of this file system.
     */
    @ThreadSafe
    File getMountPoint();

    /**
     * Returns the operating system specific name for the type of this file system.
     */
    @ThreadSafe
    String getFileSystemType();

    /**
     * Returns true if this file system is a remote file system, or false if local.
     */
    @ThreadSafe
    boolean isRemote();

    /**
     * Returns the operating system specific name for this file system.
     */
    @ThreadSafe
    String getDeviceName();
}
