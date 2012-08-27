package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.FileSystem;

import java.io.File;

public class DefaultFileSystem implements FileSystem {
    private final File mountPoint;
    private final String fileSystemType;
    private final String deviceName;
    private final boolean remote;

    public DefaultFileSystem(File mountPoint, String fileSystemType, String deviceName, boolean remote) {
        this.mountPoint = mountPoint;
        this.fileSystemType = fileSystemType;
        this.deviceName = deviceName;
        this.remote = remote;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public File getMountPoint() {
        return mountPoint;
    }

    public String getFileSystemType() {
        return fileSystemType;
    }

    public boolean isRemote() {
        return remote;
    }
}
