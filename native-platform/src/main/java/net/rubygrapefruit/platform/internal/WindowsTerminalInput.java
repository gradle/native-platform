package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalInputListener;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WindowsTerminalInput extends AbstractWindowsTerminalInput {
    private boolean raw;

    @Override
    public String toString() {
        return "Windows console on stdin";
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
                readNonRaw(listener);
            }
        }
    }

    @Override
    public boolean supportsRawMode() {
        return true;
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
