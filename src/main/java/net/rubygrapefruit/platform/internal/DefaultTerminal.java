package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalAccess;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;

import java.io.PrintStream;

public class DefaultTerminal implements Terminal {
    private final TerminalAccess.Output output;
    private final PrintStream stream;
    private Color foreground;

    public DefaultTerminal(TerminalAccess.Output output) {
        this.output = output;
        stream = output == TerminalAccess.Output.Stdout ? System.out : System.err;
    }

    public void init() {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.initTerminal(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open terminal: %s", result.getMessage()));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                reset();
            }
        });
    }

    @Override
    public TerminalSize getTerminalSize() {
        MutableTerminalSize terminalSize = new MutableTerminalSize();
        FunctionResult result = new FunctionResult();
        PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get terminal size: %s", result.getMessage()));
        }
        return terminalSize;
    }

    @Override
    public Terminal foreground(Color color) {
        stream.flush();
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch foreground color: %s", result.getMessage()));
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
            throw new NativeException(String.format("Could not switch to bold mode: %s", result.getMessage()));
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
            throw new NativeException(String.format("Could not reset terminal: %s", result.getMessage()));
        }
        return this;
    }
}
