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
    private final InputStream inputStream = new FileInputStream(FileDescriptor.in);
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
                int ch;
                try {
                    ch = inputStream.read();
                } catch (IOException e) {
                    throw new NativeException("Could not read from console", e);
                }
                if (ch < 0) {
                    listener.endInput();
                } else {
                    listener.character((char) ch);
                }
            }
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
