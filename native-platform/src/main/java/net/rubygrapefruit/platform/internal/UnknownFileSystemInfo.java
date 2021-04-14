package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.file.FileSystemInfo;

import java.io.File;

public class UnknownFileSystemInfo implements FileSystemInfo {
    private final File mountPoint;
    private final String fileSystemType;
    private final String deviceName;
    private final boolean remote;

    public UnknownFileSystemInfo(File mountPoint, String fileSystemType, String deviceName, boolean remote) {
        this.mountPoint = mountPoint;
        this.fileSystemType = fileSystemType;
        this.deviceName = deviceName;
        this.remote = remote;
    }

    @Override
    public File getMountPoint() {
        return mountPoint;
    }

    public String getFileSystemType() {
        return fileSystemType;
    }

    @Override
    public boolean isDetailsKnown() {
        return false;
    }

    @Override
    public String getDeviceName() {
        return deviceName;
    }

    @Override
    public boolean isRemote() {
        return remote;
    }

    @Override
    public boolean isCaseSensitive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCasePreserving() {
        throw new UnsupportedOperationException();
    }
}
