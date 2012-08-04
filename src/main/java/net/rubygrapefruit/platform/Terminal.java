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
     * Returns the size of the terminal.
     *
     * @throws NativeException On failure.
     */
    TerminalSize getTerminalSize() throws NativeException;

    /**
     * Sets the terminal foreground color.
     *
     * @throws NativeException On failure.
     */
    Terminal foreground(Color color) throws NativeException;

    /**
     * Switches the terminal to bold mode.
     *
     * @throws NativeException On failure.
     */
    Terminal bold() throws NativeException;

    /**
     * Switches the terminal to normal mode.
     *
     * @throws NativeException On failure.
     */
    Terminal normal() throws NativeException;

    /**
     * Switches the terminal to normal mode and restores default colors.
     *
     * @throws NativeException On failure.
     */
    Terminal reset() throws NativeException;

    /**
     * Moves the cursor the given number of characters to the left.
     *
     * @throws NativeException On failure.
     */
    Terminal cursorLeft(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters to the right.
     *
     * @throws NativeException On failure.
     */
    Terminal cursorRight(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters up.
     *
     * @throws NativeException On failure.
     */
    Terminal cursorUp(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters down.
     *
     * @throws NativeException On failure.
     */
    Terminal cursorDown(int count) throws NativeException;
}
