package net.rubygrapefruit.platform.internal;

public class PosixTerminalFunctions {
    public static native boolean isatty(int fildes);
}
