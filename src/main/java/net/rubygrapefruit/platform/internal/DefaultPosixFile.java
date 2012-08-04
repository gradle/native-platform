package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.PosixFile;
import net.rubygrapefruit.platform.internal.jni.PosixFileFunctions;

import java.io.File;

public class DefaultPosixFile implements PosixFile {
    @Override
    public void setMode(File file, int perms) {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.chmod(file.getPath(), perms, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
        }
    }

    @Override
    public int getMode(File file) {
        FunctionResult result = new FunctionResult();
        FileStat stat = new FileStat();
        PosixFileFunctions.stat(file.getPath(), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
        }
        return stat.mode;
    }
}
