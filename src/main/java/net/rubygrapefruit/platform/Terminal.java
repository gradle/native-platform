package net.rubygrapefruit.platform;

/**
 * Allows the terminal/console to be manipulated.
 *
 * Supported on Linux, OS X, Windows.
 */
public interface Terminal {
    enum Color {
        Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
    }

    /**
     * Returns true if this terminal supports setting text attributes, such as bold.
     */
    boolean supportsTextAttributes();

    /**
     * Returns true if this terminal supports setting output colors.
     */
    boolean supportsColor();

    /**
     * Returns true if this terminal supports moving the cursor.
     */
    boolean supportsCursorMotion();

    /**
     * Returns the size of the terminal. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    TerminalSize getTerminalSize() throws NativeException;

    /**
     * Sets the terminal foreground color, if supported. Does nothing if this terminal does not support setting the
     * foreground color.
     *
     * @throws NativeException On failure.
     */
    Terminal foreground(Color color) throws NativeException;

    /**
     * Switches the terminal to bold mode, if supported. Does nothing if this terminal does not support bold mode.
     *
     * @throws NativeException On failure.
     */
    Terminal bold() throws NativeException;

    /**
     * Switches the terminal to normal mode. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    Terminal normal() throws NativeException;

    /**
     * Switches the terminal to normal mode and restores default colors. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    Terminal reset() throws NativeException;

    /**
     * Moves the cursor the given number of characters to the left.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    Terminal cursorLeft(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters to the right.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    Terminal cursorRight(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters up.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    Terminal cursorUp(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters down.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    Terminal cursorDown(int count) throws NativeException;

    /**
     * Moves the cursor to the start of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    Terminal cursorStartOfLine() throws NativeException;

    /**
     * Clears characters from the cursor position to the end of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support clearing.
     */
    Terminal clearToEndOfLine() throws NativeException;
}
