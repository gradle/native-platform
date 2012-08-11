package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FileSystemList;
import net.rubygrapefruit.platform.internal.FunctionResult;

public class PosixFileSystemFunctions {
    public static native void listFileSystems(FileSystemList fileSystems, FunctionResult result);
}
