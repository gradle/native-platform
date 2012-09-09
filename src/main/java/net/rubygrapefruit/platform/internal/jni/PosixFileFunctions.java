package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FileStat;
import net.rubygrapefruit.platform.internal.FunctionResult;

public class PosixFileFunctions {
    public static native void chmod(String file, int perms, FunctionResult result);

    public static native void stat(String file, FileStat stat, FunctionResult result);

    public static native void symlink(String file, String content, FunctionResult result);

    public static native String readlink(String file, FunctionResult result);
}
