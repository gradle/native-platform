package net.rubygrapefruit.platform.internal;

public class PosixTerminalFunctions {
    public static native boolean isatty(int filedes);

    public static native void getTerminalSize(int filedes, MutableTerminalSize size, FunctionResult result);
}
