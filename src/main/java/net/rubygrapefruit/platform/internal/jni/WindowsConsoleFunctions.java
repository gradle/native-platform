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

    public static native void left(int count, FunctionResult result);

    public static native void right(int count, FunctionResult result);

    public static native void up(int count, FunctionResult result);

    public static native void down(int count, FunctionResult result);

    public static native void startLine(FunctionResult result);

    public static native void clearToEndOfLine(FunctionResult result);
}
