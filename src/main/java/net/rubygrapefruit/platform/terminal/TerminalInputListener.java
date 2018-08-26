package net.rubygrapefruit.platform.terminal;

/**
 * Receives terminal input.
 */
public interface TerminalInputListener {
    enum Key {
        // Order is significant
        Enter,
        UpArrow,
        DownArrow,
        LeftArrow,
        RightArrow,
        Home,
        End,
        EraseBack,
        EraseForward,
    }

    /**
     * Called when a character is typed. Note that this method is not called for the 'enter' key.
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
