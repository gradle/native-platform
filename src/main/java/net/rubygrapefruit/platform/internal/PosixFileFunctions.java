package net.rubygrapefruit.platform.internal;

public class PosixFileFunctions {
    public static native void chmod(String file, int perms, FunctionResult result);

    public static native void stat(String file, FileStat stat, FunctionResult result);
}
