package net.rubygrapefruit.platform.internal;

public class TerminfoFunctions {
    /**
     * Sets up terminal info and switches output to normal mode.
     */
    public static native void initTerminal(int filedes, FunctionResult result);

    public static native void bold(FunctionResult result);

    public static native void reset(FunctionResult result);

    /**
     * Set the foreground color to the given ansi color.
     */
    public static native void foreground(int ansiColor, FunctionResult result);
}
