package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

public class TerminfoTerminalAccess implements TerminalAccess {
    private static Output currentlyOpen;
    private static TerminfoTerminal current;

    public boolean isTerminal(Output output) {
        return PosixTerminalFunctions.isatty(output.ordinal());
    }

    public Terminal getTerminal(Output output) {
        if (currentlyOpen != null && currentlyOpen != output) {
            throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
        }
        if (current == null) {
            current = new TerminfoTerminal(output);
            current.init();
        }

        currentlyOpen = output;
        return current;
    }
}
