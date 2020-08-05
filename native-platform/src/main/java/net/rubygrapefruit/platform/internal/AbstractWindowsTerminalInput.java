package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalInputListener;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractWindowsTerminalInput implements TerminalInput {
    private final PeekInputStream inputStream = new PeekInputStream(new FileInputStream(FileDescriptor.in));
    final Object lock = new Object();

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    void readNonRaw(TerminalInputListener listener) {
        if (peek(0) == '\n' || (peek(0) == '\r' && peek(1) == '\n')) {
            inputStream.consumeAll();
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

    int peek(int i) {
        try {
            return inputStream.peek(i);
        } catch (IOException e) {
            throw new NativeException("Could not read from console.", e);
        }
    }

    int next() {
        try {
            return inputStream.read();
        } catch (IOException e) {
            throw new NativeException("Could not read from console.", e);
        }
    }
}
