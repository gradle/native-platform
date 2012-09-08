package net.rubygrapefruit.platform;

/**
 * The size of a terminal. This is a snapshot view and does not change.
 */
@ThreadSafe
public interface TerminalSize {
    /**
     * Returns the number of character columns in the terminal.
     */
    @ThreadSafe
    public int getCols();

    /**
     * Returns the number of character rows in the terminal.
     */
    @ThreadSafe
    public int getRows();
}
