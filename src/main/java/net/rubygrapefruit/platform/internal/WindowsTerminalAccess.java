package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

public class WindowsTerminalAccess implements TerminalAccess {
    private static Output currentlyOpen;

    @Override
    public boolean isTerminal(Output output) {
        FunctionResult result = new FunctionResult();
        boolean console = WindowsConsoleFunctions.isConsole(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine if %s is a console: %s", output,
                    result.getMessage()));
        }
        return console;
    }

    @Override
    public Terminal getTerminal(Output output) {
        if (currentlyOpen != null) {
            throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
        }

        WindowsTerminal terminal = new WindowsTerminal(output);
        terminal.init();

        currentlyOpen = output;
        return terminal;
    }
}
