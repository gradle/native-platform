package net.rubygrapefruit.platform.terminal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.InputStream;

/**
 * Allows input to be received from the terminal.
 *
 * <p>On UNIX based platforms, this provides access to the terminal. On Windows platforms, this provides access to the
 * console.
 *
 * <p>To create an instance of this interface use the {@link Terminals#getTerminalInput()} method.
 */
@ThreadSafe
public interface TerminalInput {
    /**
     * Returns an input stream that can be used to read characters from this terminal.
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
