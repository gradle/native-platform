package net.rubygrapefruit.platform;

import java.io.InputStream;

/**
 * Allows input to be received from the terminal.
 */
@ThreadSafe
public interface TerminalInput {
    /**
     * Returns an input stream that can be used to read characters from the terminal.
     */
    InputStream getInputStream();

    /**
     * Switches the input to raw mode. Characters are delivered as they are typed, are not echoed and are not processed.
     */
    TerminalInput rawMode() throws NativeException;

    /**
     * Resets the input to default mode.
     */
    TerminalInput reset();
}
