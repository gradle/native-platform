package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;
import net.rubygrapefruit.platform.internal.MutableTerminalSize;

public class PosixTerminalFunctions {
    public static native boolean isatty(int filedes);

    public static native void getTerminalSize(int filedes, MutableTerminalSize size, FunctionResult result);
}
