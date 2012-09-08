package net.rubygrapefruit.platform;

/**
 * Allows the terminal/console to be manipulated.
 *
 * <p>On UNIX based platforms, this provides access to the terminal. On Windows platforms, this provides access to the
 * console.
 * </p>
 */
@ThreadSafe
public interface Terminal {
    /**
     * Colors supported by a terminal.
     */
    enum Color {
        Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
    }

    /**
     * Returns true if this terminal supports setting text attributes, such as bold.
     */
    @ThreadSafe
    boolean supportsTextAttributes();

    /**
     * Returns true if this terminal supports setting output colors.
     */
    @ThreadSafe
    boolean supportsColor();

    /**
     * Returns true if this terminal supports moving the cursor.
     */
    @ThreadSafe
    boolean supportsCursorMotion();

    /**
     * Returns the size of the terminal. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    TerminalSize getTerminalSize() throws NativeException;

    /**
     * Sets the terminal foreground color, if supported. Does nothing if this terminal does not support setting the
     * foreground color.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    Terminal foreground(Color color) throws NativeException;

    /**
     * Switches the terminal to bold mode, if supported. Does nothing if this terminal does not support bold mode.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    Terminal bold() throws NativeException;

    /**
     * Switches the terminal to normal mode. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    Terminal normal() throws NativeException;

    /**
     * Switches the terminal to normal mode and restores default colors. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    Terminal reset() throws NativeException;

    /**
     * Moves the cursor the given number of characters to the left.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    @ThreadSafe
    Terminal cursorLeft(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters to the right.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    @ThreadSafe
    Terminal cursorRight(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters up.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    @ThreadSafe
    Terminal cursorUp(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters down.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    @ThreadSafe
    Terminal cursorDown(int count) throws NativeException;

    /**
     * Moves the cursor to the start of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    @ThreadSafe
    Terminal cursorStartOfLine() throws NativeException;

    /**
     * Clears characters from the cursor position to the end of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support clearing.
     */
    @ThreadSafe
    Terminal clearToEndOfLine() throws NativeException;
}
