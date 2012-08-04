package net.rubygrapefruit.platform.internal;

public class WindowsConsoleFunctions {
    public static native boolean isConsole(int filedes, FunctionResult result);

    public static native void getConsoleSize(int filedes, MutableTerminalSize size, FunctionResult result);
}
