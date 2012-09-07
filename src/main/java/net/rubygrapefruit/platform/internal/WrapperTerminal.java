package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalSize;

import java.io.PrintStream;

/**
 * A {@link Terminal} implementation that wraps another to add thread safety.
 */
public class WrapperTerminal extends AbstractTerminal {
    private final AbstractTerminal terminal;
    private final PrintStream stream;
    private final Object lock = new Object();

    public WrapperTerminal(PrintStream stream, AbstractTerminal terminal) {
        this.stream = stream;
        this.terminal = terminal;
    }

    @Override
    protected void init() {
        stream.flush();
        terminal.init();
    }

    @Override
    public TerminalSize getTerminalSize() throws NativeException {
        return terminal.getTerminalSize();
    }

    @Override
    public boolean supportsColor() {
        return terminal.supportsColor();
    }

    @Override
    public boolean supportsCursorMotion() {
        return terminal.supportsCursorMotion();
    }

    @Override
    public boolean supportsTextAttributes() {
        return terminal.supportsTextAttributes();
    }

    @Override
    public Terminal normal() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.normal();
        }
        return this;
    }

    @Override
    public Terminal bold() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.bold();
        }
        return this;
    }

    @Override
    public Terminal reset() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.reset();
        }
        return this;
    }

    @Override
    public Terminal foreground(Color color) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.foreground(color);
        }
        return this;
    }

    @Override
    public Terminal cursorLeft(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorLeft(count);
        }
        return this;
    }

    @Override
    public Terminal cursorRight(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorRight(count);
        }
        return this;
    }

    @Override
    public Terminal cursorUp(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorUp(count);
        }
        return this;
    }

    @Override
    public Terminal cursorDown(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorDown(count);
        }
        return this;
    }

    @Override
    public Terminal cursorStartOfLine() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorStartOfLine();
        }
        return this;
    }

    @Override
    public Terminal clearToEndOfLine() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.clearToEndOfLine();
        }
        return this;
    }
}
