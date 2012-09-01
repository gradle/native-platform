package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FileStat;
import net.rubygrapefruit.platform.internal.FunctionResult;

public class PosixFileFunctions {
    public static native void chmod(byte[] file, int perms, FunctionResult result);

    public static native void stat(byte[] file, FileStat stat, FunctionResult result);

    public static native void symlink(byte[] file, byte[] content, FunctionResult result);

    public static native byte[] readlink(byte[] file, FunctionResult result);
}
