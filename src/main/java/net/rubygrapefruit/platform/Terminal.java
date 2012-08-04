package net.rubygrapefruit.platform;

public interface Terminal {
    enum Color {
        Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
    }

    /**
     * Returns the size of the terminal.
     */
    TerminalSize getTerminalSize();

    /**
     * Sets the terminal foreground color.
     */
    Terminal foreground(Color color);

    /**
     * Switches the terminal to bold mode.
     */
    Terminal bold();

    /**
     * Switches the terminal to normal mode.
     */
    Terminal normal();

    /**
     * Switches the terminal to normal mode and restores default colors.
     */
    Terminal reset();
}
