package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

public class TerminfoFunctions {
    /**
     * Sets up terminal info and switches output to normal mode.
     */
    public static native void initTerminal(int filedes, FunctionResult result);

    public static native void bold(FunctionResult result);

    public static native void reset(FunctionResult result);

    public static native void foreground(int ansiColor, FunctionResult result);

    public static native void left(int count, FunctionResult result);

    public static native void right(int count, FunctionResult result);

    public static native void up(int count, FunctionResult result);

    public static native void down(int count, FunctionResult result);
}
