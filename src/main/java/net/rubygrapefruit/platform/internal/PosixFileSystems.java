package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.FileSystem;
import net.rubygrapefruit.platform.FileSystems;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.PosixFileSystemFunctions;

import java.util.List;

public class PosixFileSystems implements FileSystems {
    public List<FileSystem> getFileSystems() {
        FunctionResult result = new FunctionResult();
        FileSystemList fileSystems = new FileSystemList();
        PosixFileSystemFunctions.listFileSystems(fileSystems, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not query file systems: %s", result.getMessage()));
        }
        return fileSystems.fileSystems;
    }
}
