package net.rubygrapefruit.platform;

public interface Terminal {
    TerminalSize getTerminalSize();

    /**
     * Switches the terminal to bold mode.
     */
    Terminal bold();

    /**
     * Switches the terminal to bold mode, outputs the given text, then switches to normal mode.
     */
    Terminal bold(String output);

    /**
     * Switches the terminal to normal mode.
     */
    Terminal normal();
}
