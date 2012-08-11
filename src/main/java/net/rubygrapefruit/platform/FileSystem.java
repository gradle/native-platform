package net.rubygrapefruit.platform;

import java.io.File;

public interface FileSystem {
    File getMountPoint();

    String getFileSystemType();

    boolean isRemote();

    String getDeviceName();
}
