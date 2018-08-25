package net.rubygrapefruit.platform;

import java.io.InputStream;

/**
 * Allows input to be received from the terminal.
 */
@ThreadSafe
public interface TerminalInput {
    /**
     * Returns an input stream that can be used to read characters from this terminal. Control keys are discarded.
     */
    InputStream getInputStream();

    /**
     * Reads characters and control keys from this terminal.
     */
    void read(TerminalInputListener listener) throws NativeException;

    /**
     * Switches this terminal to raw mode. Keys are delivered as they are typed, are not echoed and are not processed.
     */
    TerminalInput rawMode() throws NativeException;

    /**
     * Resets this terminal to its default mode.
     */
    TerminalInput reset() throws NativeException;
}
