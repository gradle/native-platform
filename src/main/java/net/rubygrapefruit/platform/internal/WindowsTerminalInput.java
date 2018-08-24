package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.TerminalInput;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;

public class WindowsTerminalInput implements TerminalInput {
    private final InputStream inputStream = new FileInputStream(FileDescriptor.in);
    private final Object lock = new Object();

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public TerminalInput rawMode() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.rawInputMode(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch console input to raw mode: %s", result.getMessage()));
            }
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
        }
        return this;
    }
}
