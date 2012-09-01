package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;
import net.rubygrapefruit.platform.internal.MutableSystemInfo;

public class NativeLibraryFunctions {
    public static final int VERSION = 7;

    public static native int getVersion();

    public static native void getSystemInfo(MutableSystemInfo systemInfo, FunctionResult result);
}
