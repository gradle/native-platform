package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.PosixFile;
import net.rubygrapefruit.platform.internal.jni.PosixFileFunctions;

import java.io.File;

public class DefaultPosixFile implements PosixFile {
    public void setMode(File file, int perms) {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.chmod(file.getPath(), perms, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
        }
    }

    public int getMode(File file) {
        FunctionResult result = new FunctionResult();
        FileStat stat = new FileStat();
        PosixFileFunctions.stat(file.getPath(), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
        }
        return stat.mode;
    }

    @Override
    public String readLink(File link) throws NativeException {
        FunctionResult result = new FunctionResult();
        String contents = PosixFileFunctions.readlink(link.getPath(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not read symlink %s: %s", link, result.getMessage()));
        }
        return contents;
    }

    @Override
    public void symlink(File link, String contents) throws NativeException {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.symlink(link.getPath(), contents, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not create symlink %s: %s", link, result.getMessage()));
        }
    }
}
