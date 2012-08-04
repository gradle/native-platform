package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;

import java.io.PrintStream;

public class TerminfoTerminal extends AbstractTerminal {
    private final TerminalAccess.Output output;
    private final PrintStream stream;
    private Color foreground;

    public TerminfoTerminal(TerminalAccess.Output output) {
        this.output = output;
        stream = output == TerminalAccess.Output.Stdout ? System.out : System.err;
    }

    @Override
    public String toString() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void doInit() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.initTerminal(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open terminal for %s: %s", this, result.getMessage()));
        }
    }

    @Override
    public TerminalSize getTerminalSize() {
        MutableTerminalSize terminalSize = new MutableTerminalSize();
        FunctionResult result = new FunctionResult();
        PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get terminal size for %s: %s", this, result.getMessage()));
        }
        return terminalSize;
    }

    @Override
    public Terminal foreground(Color color) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch foreground color for %s: %s", this, result.getMessage()));
        }
        foreground = color;
        return this;
    }

    @Override
    public Terminal bold() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.bold(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch to bold mode for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal normal() {
        reset();
        if (foreground != null) {
            foreground(foreground);
        }
        return this;
    }

    @Override
    public Terminal reset() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.reset(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not reset terminal for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal cursorDown(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.down(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor down for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal cursorUp(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.up(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor up for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal cursorLeft(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.left(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor left for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal cursorRight(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.right(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor right for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal cursorStartOfLine() throws NativeException {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.startLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor to start of line for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    @Override
    public Terminal clearToEndOfLine() throws NativeException {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.clearToEndOfLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not clear to end of line for %s: %s", this, result.getMessage()));
        }
        return this;
    }
}
