package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;

import java.io.PrintStream;

public class TerminfoTerminal extends AbstractTerminal {
    private final Terminals.Output output;
    private final PrintStream stream;
    private final TerminalCapabilities capabilities = new TerminalCapabilities();
    private Color foreground;

    public TerminfoTerminal(Terminals.Output output) {
        this.output = output;
        stream = output == Terminals.Output.Stdout ? System.out : System.err;
    }

    @Override
    public String toString() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void doInit() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.initTerminal(output.ordinal(), capabilities, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open terminal for %s: %s", this, result.getMessage()));
        }
    }

    public TerminalSize getTerminalSize() {
        MutableTerminalSize terminalSize = new MutableTerminalSize();
        FunctionResult result = new FunctionResult();
        PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get terminal size for %s: %s", this, result.getMessage()));
        }
        return terminalSize;
    }

    public boolean supportsColor() {
        return capabilities.colors;
    }

    public boolean supportsCursorMotion() {
        return capabilities.cursorMotion;
    }

    public boolean supportsTextAttributes() {
        return capabilities.textAttributes;
    }

    public Terminal foreground(Color color) {
        if (!capabilities.colors) {
            return this;
        }

        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch foreground color for %s: %s", this,
                    result.getMessage()));
        }
        foreground = color;
        return this;
    }

    public Terminal bold() {
        if (!capabilities.textAttributes) {
            return this;
        }

        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.bold(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch to bold mode for %s: %s", this,
                    result.getMessage()));
        }
        return this;
    }

    public Terminal normal() {
        reset();
        if (foreground != null) {
            foreground(foreground);
        }
        return this;
    }

    public Terminal reset() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.reset(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not reset terminal for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    public Terminal cursorDown(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.down(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor down for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    public Terminal cursorUp(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.up(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor up for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    public Terminal cursorLeft(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.left(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor left for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    public Terminal cursorRight(int count) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.right(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor right for %s: %s", this, result.getMessage()));
        }
        return this;
    }

    public Terminal cursorStartOfLine() throws NativeException {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.startLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor to start of line for %s: %s", this, result.getMessage()));
        }
        return this;
    }

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
