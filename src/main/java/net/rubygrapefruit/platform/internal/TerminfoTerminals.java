package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

import java.io.PrintStream;

public class TerminfoTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        return PosixTerminalFunctions.isatty(output.ordinal());
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        PrintStream stream = output == Terminals.Output.Stdout ? System.out : System.err;
        return new WrapperTerminal(stream, new TerminfoTerminal(output));
    }
}
