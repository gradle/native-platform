package net.rubygrapefruit.platform;

/**
 * Provides access to the terminal/console.
 *
 * Supported on Linux, OS X, Windows.
 */
public interface TerminalAccess extends NativeIntegration {
    enum Output {Stdout, Stderr}

    /**
     * Returns true if the given output is attached to a terminal.
     *
     * @throws NativeException On failure.
     */
    boolean isTerminal(Output output) throws NativeException;

    /**
     * Returns the terminal attached to the given output.
     *
     * @throws NativeException When the output is not attached to a terminal.
     */
    Terminal getTerminal(Output output) throws NativeException;
}
