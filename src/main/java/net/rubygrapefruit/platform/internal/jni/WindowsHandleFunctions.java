package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

public class WindowsHandleFunctions {
    public static native void markStandardHandlesUninheritable(FunctionResult result);

    public static native void restoreStandardHandles(FunctionResult result);
}
