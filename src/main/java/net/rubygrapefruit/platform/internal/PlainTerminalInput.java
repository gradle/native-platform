package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalInputListener;

public class PlainTerminalInput extends AbstractWindowsTerminalInput {
    @Override
    public String toString() {
        return "plain input on stdin";
    }

    @Override
    public void read(TerminalInputListener listener) throws NativeException {
        synchronized (lock) {
            readNonRaw(listener);
        }
    }

    @Override
    public boolean supportsRawMode() {
        return false;
    }

    @Override
    public TerminalInput rawMode() throws NativeException {
        throw new NativeException("Raw mode is not supported for this terminal.");
    }

    @Override
    public TerminalInput reset() throws NativeException {
        // ignore
        return this;
    }
}
