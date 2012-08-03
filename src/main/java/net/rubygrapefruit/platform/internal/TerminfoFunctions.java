package net.rubygrapefruit.platform.internal;

public class TerminfoFunctions {
    /**
     * Sets up terminal info and switches output to normal mode.
     */
    public static native void initTerminal(int filedes, FunctionResult result);

    public static native void bold(int filedes, FunctionResult result);

    public static native void normal(int filedes, FunctionResult result);
}
