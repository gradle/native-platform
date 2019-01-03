package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

class AnsiTerminals implements Terminals {
    private final Terminals delegate;
    private final AnsiTerminal output;

    public AnsiTerminals(Terminals delegate) {
        this.delegate = delegate;
        this.output = new AnsiTerminal(Output.Stdout);
    }

    @Override
    public Terminals withAnsiOutput() {
        return this;
    }

    @Override
    public boolean isTerminal(Output output) throws NativeException {
        if (output == Output.Stdout) {
            return true;
        }
        return delegate.isTerminal(Output.Stderr);
    }

    @Override
    public boolean isTerminalInput() throws NativeException {
        return delegate.isTerminalInput();
    }

    @Override
    public TerminalOutput getTerminal(Output output) throws NativeException {
        if (output == Output.Stdout) {
            return this.output;
        }
        return delegate.getTerminal(output);
    }

    @Override
    public TerminalInput getTerminalInput() throws NativeException {
        return delegate.getTerminalInput();
    }
}
