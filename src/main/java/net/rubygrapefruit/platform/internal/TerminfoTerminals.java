package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

public class TerminfoTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        return PosixTerminalFunctions.isatty(output.ordinal());
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        return new TerminfoTerminal(output);
    }
}
