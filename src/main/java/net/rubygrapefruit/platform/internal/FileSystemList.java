package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.FileSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileSystemList {
    public final List<FileSystem> fileSystems = new ArrayList<FileSystem>();

    public void add(String mountPoint, String fileSystemName, String deviceName, boolean remote) {
        fileSystems.add(new DefaultFileSystem(new File(mountPoint), fileSystemName, deviceName, remote));
    }
}
