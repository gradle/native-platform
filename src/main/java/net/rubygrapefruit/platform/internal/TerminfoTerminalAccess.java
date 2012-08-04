package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

public class TerminfoTerminalAccess implements TerminalAccess {
    private static Output currentlyOpen;

    @Override
    public boolean isTerminal(Output output) {
        return PosixTerminalFunctions.isatty(output.ordinal());
    }

    @Override
    public Terminal getTerminal(Output output) {
        if (currentlyOpen != null) {
            throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
        }

        TerminfoTerminal terminal = new TerminfoTerminal(output);
        terminal.init();

        currentlyOpen = output;
        return terminal;
    }
}
