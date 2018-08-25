package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.TerminalInput;
import net.rubygrapefruit.platform.TerminalInputListener;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PosixTerminalInput implements TerminalInput {
    private final InputStream inputStream = new FileInputStream(FileDescriptor.in);
    private final Object lock = new Object();
    private byte upArrow = 65;
    private byte downArrow = 66;
    private byte leftArrow = 68;
    private byte rightArrow = 67;

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void read(TerminalInputListener listener) {
        synchronized (lock) {
            int ch = next();
            if (ch < 0) {
                listener.endInput();
                return;
            }
            if (ch == 27) {
                int ch2 = next();
                if (ch2 < 0) {
                    listener.character((char) ch);
                    listener.endInput();
                    return;
                }
                if (ch2 == 91) {
                    int ch3 = next();
                    if (ch3 < 0) {
                        listener.character((char) ch);
                        listener.character((char) ch2);
                        listener.endInput();
                    } else if (ch3 == upArrow) {
                        listener.controlKey(TerminalInputListener.Key.UpArrow);
                    } else if (ch3 == downArrow) {
                        listener.controlKey(TerminalInputListener.Key.DownArrow);
                    } else if (ch3 == leftArrow) {
                        listener.controlKey(TerminalInputListener.Key.LeftArrow);
                    } else if (ch3 == rightArrow) {
                        listener.controlKey(TerminalInputListener.Key.RightArrow);
                    } else {
                        listener.character((char) ch);
                        listener.character((char) ch2);
                        listener.character((char) ch3);
                    }
                } else {
                    listener.character((char) ch);
                    listener.character((char) ch2);
                }
            } else {
                listener.character((char) ch);
            }
        }
    }

    private int next() {
        int ch;
        try {
            ch = inputStream.read();
        } catch (IOException e) {
            throw new NativeException("Could not read from terminal.", e);
        }
        return ch;
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
