package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.TerminalSize;

public class MutableTerminalSize implements TerminalSize {
    int rows;
    int cols;

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }
}
