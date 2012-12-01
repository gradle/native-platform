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
    public String toString() {
        return terminal.toString();
    }

    public TerminalSize getTerminalSize() throws NativeException {
        return terminal.getTerminalSize();
    }

    public boolean supportsColor() {
        return terminal.supportsColor();
    }

    public boolean supportsCursorMotion() {
        return terminal.supportsCursorMotion();
    }

    public boolean supportsTextAttributes() {
        return terminal.supportsTextAttributes();
    }

    public Terminal normal() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.normal();
        }
        return this;
    }

    public Terminal bold() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.bold();
        }
        return this;
    }

    public Terminal reset() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.reset();
        }
        return this;
    }

    public Terminal foreground(Color color) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.foreground(color);
        }
        return this;
    }

    public Terminal cursorLeft(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorLeft(count);
        }
        return this;
    }

    public Terminal cursorRight(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorRight(count);
        }
        return this;
    }

    public Terminal cursorUp(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorUp(count);
        }
        return this;
    }

    public Terminal cursorDown(int count) throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorDown(count);
        }
        return this;
    }

    public Terminal cursorStartOfLine() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.cursorStartOfLine();
        }
        return this;
    }

    public Terminal clearToEndOfLine() throws NativeException {
        stream.flush();
        synchronized (lock) {
            terminal.clearToEndOfLine();
        }
        return this;
    }
}
