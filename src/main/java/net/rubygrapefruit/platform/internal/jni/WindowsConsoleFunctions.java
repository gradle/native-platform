package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;
import net.rubygrapefruit.platform.internal.MutableTerminalSize;

public class WindowsConsoleFunctions {
    public static native boolean isConsole(int filedes, FunctionResult result);

    public static native void getConsoleSize(int filedes, MutableTerminalSize size, FunctionResult result);

    public static native void initConsole(int filedes, FunctionResult result);

    public static native void bold(FunctionResult result);

    public static native void normal(FunctionResult result);

    public static native void reset(FunctionResult result);

    public static native void foreground(int ansiColor, FunctionResult result);
}
