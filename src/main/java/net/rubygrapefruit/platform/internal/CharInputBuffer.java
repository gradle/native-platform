package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.terminal.TerminalInputListener;

public class CharInputBuffer {
    char ch;
    TerminalInputListener.Key key;

    public void character(char ch) {
        this.ch = ch;
    }

    public void key(int value) {
        this.key = TerminalInputListener.Key.values()[value];
    }

    public void applyTo(TerminalInputListener listener) {
        if (key != null) {
            listener.controlKey(key);
        } else if (ch != 0) {
            listener.character(ch);
        } else {
            listener.endInput();
        }
        key = null;
        ch = 0;
    }
}
