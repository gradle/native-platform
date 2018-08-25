package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.TerminalInput;
import net.rubygrapefruit.platform.TerminalInputListener;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WindowsTerminalInput implements TerminalInput {
    private final PeekInputStream inputStream = new PeekInputStream(new FileInputStream(FileDescriptor.in));
    private final Object lock = new Object();
    private boolean raw;

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void read(TerminalInputListener listener) {
        synchronized (lock) {
            if (raw) {
                FunctionResult result = new FunctionResult();
                CharInputBuffer buffer = new CharInputBuffer();
                WindowsConsoleFunctions.readInput(buffer, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could not read from console: %s", result.getMessage()));
                }
                buffer.applyTo(listener);
            } else {
                if (peek(0) == '\r' && peek(1) == '\n') {
                    inputStream.consume();
                    listener.controlKey(TerminalInputListener.Key.Enter);
                    return;
                }
                int ch = next();
                if (ch < 0) {
                    listener.endInput();
                } else {
                    listener.character((char) ch);
                }
            }
        }
    }

    private int peek(int i) {
        try {
            return inputStream.peek(i);
        } catch (IOException e) {
            throw new NativeException("Could not read from console.", e);
        }
    }

    private int next() {
        try {
            return inputStream.read();
        } catch (IOException e) {
            throw new NativeException("Could not read from console.", e);
        }
    }

    @Override
    public TerminalInput rawMode() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.rawInputMode(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch console input to raw mode: %s", result.getMessage()));
            }
            raw = true;
        }
        return this;
    }

    @Override
    public TerminalInput reset() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.resetInputMode(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not reset console input mode: %s", result.getMessage()));
            }
            raw = false;
        }
        return this;
    }
}
