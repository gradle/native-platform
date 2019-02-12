package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalInputListener;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Assumes vt100 input control sequences: http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 */
public class PosixTerminalInput implements TerminalInput {
    private final PeekInputStream inputStream = new PeekInputStream(new FileInputStream(FileDescriptor.in));
    private final Object lock = new Object();
    private static final int UP_ARROW = 65;
    private static final int DOWN_ARROW = 66;
    private static final int RIGHT_ARROW = 67;
    private static final int LEFT_ARROW = 68;
    private static final int BACK_TAB = 90;
    private static final int END = 70;
    private static final int END1 = 52;
    private static final int HOME = 72;
    private static final int HOME1 = 49;
    private static final int ERASE1 = 51;
    private static final int PAGE_UP = 53;
    private static final int PAGE_DOWN = 54;
    private static final int TILDE = 126;

    @Override
    public String toString() {
        return "POSIX input on stdin";
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void read(TerminalInputListener listener) {
        synchronized (lock) {
            if (peek(0) == 27 && peek(1) == 91) {
                int ch = peek(2);
                if (ch == UP_ARROW) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.UpArrow);
                    return;
                } else if (ch == DOWN_ARROW) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.DownArrow);
                    return;
                } else if (ch == LEFT_ARROW) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.LeftArrow);
                    return;
                } else if (ch == RIGHT_ARROW) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.RightArrow);
                    return;
                } else if (ch == BACK_TAB) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.BackTab);
                    return;
                } else if (ch == HOME) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.Home);
                    return;
                } else if (ch == END) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.End);
                    return;
                } else if (ch == ERASE1 && peek(3) == TILDE) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.EraseForward);
                    return;
                } else if (ch == HOME1 && peek(3) == TILDE) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.Home);
                    return;
                } else if (ch == END1 && peek(3) == TILDE) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.End);
                    return;
                } else if (ch == PAGE_UP && peek(3) == TILDE) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.PageUp);
                    return;
                } else if (ch == PAGE_DOWN && peek(3) == TILDE) {
                    inputStream.consumeAll();
                    listener.controlKey(TerminalInputListener.Key.PageDown);
                    return;
                }
            }
            int ch = next();
            if (ch < 0) {
                listener.endInput();
            } else if (ch == '\n') {
                listener.controlKey(TerminalInputListener.Key.Enter);
            } else if (ch == 127 || ch == 8) {
                listener.controlKey(TerminalInputListener.Key.EraseBack);
            } else if (ch == 4) {
                // ctrl-d
                listener.endInput();
            } else {
                listener.character((char) ch);
            }
        }
    }

    private int peek(int i) {
        try {
            return inputStream.peek(i);
        } catch (IOException e) {
            throw new NativeException("Could not read from terminal.", e);
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
    public boolean supportsRawMode() {
        return true;
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
