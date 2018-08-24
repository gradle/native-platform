package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.TerminalInput;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;

public class PosixTerminalInput implements TerminalInput {
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
            PosixTerminalFunctions.rawInputMode(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch terminal input to raw mode: %s", result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalInput reset() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            PosixTerminalFunctions.resetInputMode(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not reset terminal input mode: %s", result.getMessage()));
            }
        }
        return this;
    }
}
