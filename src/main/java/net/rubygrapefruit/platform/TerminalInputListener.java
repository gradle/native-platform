package net.rubygrapefruit.platform;

/**
 * Receives terminal input.
 */
public interface TerminalInputListener {
    enum Key {
        UpArrow,
        DownArrow,
        LeftArrow,
        RightArrow
    }

    /**
     * Called when a character is typed.
     */
    void character(char ch);

    /**
     * Called when a control key is typed.
     */
    void controlKey(Key key);

    /**
     * Called on the end of input.
     */
    void endInput();
}
