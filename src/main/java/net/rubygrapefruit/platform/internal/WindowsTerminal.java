package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

public class WindowsTerminal extends AbstractTerminal {
    private final TerminalAccess.Output output;

    public WindowsTerminal(TerminalAccess.Output output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void doInit() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.initConsole(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open console for %s: %s", this, result.getMessage()));
        }
    }

    @Override
    public TerminalSize getTerminalSize() {
        FunctionResult result = new FunctionResult();
        MutableTerminalSize size = new MutableTerminalSize();
        WindowsConsoleFunctions.getConsoleSize(output.ordinal(), size, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine console size for %s: %s", this, result.getMessage()));
        }
        return size;
    }

    @Override
    public Terminal bold() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.bold(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch console to bold mode for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal foreground(Color color) {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not change console foreground color for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal normal() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.normal(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch console to normal mode for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal reset() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.reset(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not reset console for %s: %s", this, result.getMessage()));
        }
        return this;
    }
}
