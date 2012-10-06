package net.rubygrapefruit.platform;

/**
 * Provides access to the terminal/console.
 *
 * <p>On UNIX based platforms, this provides access to the terminal. On Windows platforms, this provides access to the
 * console.
 * </p>
 */
@ThreadSafe
public interface Terminals extends NativeIntegration {
    /**
     * System outputs.
     */
    enum Output {Stdout, Stderr}

    /**
     * Returns true if the given output is attached to a terminal.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    boolean isTerminal(Output output) throws NativeException;

    /**
     * Returns the terminal attached to the given output.
     *
     * @return The terminal. Never returns null.
     * @throws NativeException When the output is not attached to a terminal.
     */
    @ThreadSafe
    Terminal getTerminal(Output output) throws NativeException;
}
