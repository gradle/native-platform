package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.TerminalInputListener;

class YesNoListener implements TerminalInputListener {
    private final boolean defaultValue;
    private Boolean selected;
    private boolean finished;

    YesNoListener(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isFinished() {
        return finished;
    }

    public Boolean getSelected() {
        return selected;
    }

    @Override
    public void character(char ch) {
        if (ch == 'y') {
            selected = true;
            finished = true;
        } else if (ch == 'n') {
            selected = false;
            finished = true;
        }
    }

    @Override
    public void controlKey(Key key) {
        if (key == Key.Enter) {
            selected = defaultValue;
            finished = true;
        }
    }

    @Override
    public void endInput() {
        finished = true;
    }
}
