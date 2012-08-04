package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

public class WindowsTerminal implements Terminal {
    private final TerminalAccess.Output output;

    public WindowsTerminal(TerminalAccess.Output output) {
        this.output = output;
    }

    @Override
    public TerminalSize getTerminalSize() {
        FunctionResult result = new FunctionResult();
        MutableTerminalSize size = new MutableTerminalSize();
        WindowsConsoleFunctions.getConsoleSize(output.ordinal(), size, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine terminal size: %s", result.getMessage()));
        }
        return size;
    }

    @Override
    public Terminal bold() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Terminal foreground(Color color) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Terminal normal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Terminal reset() {
        throw new UnsupportedOperationException();
    }
}
