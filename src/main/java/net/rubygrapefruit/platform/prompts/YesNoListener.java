package net.rubygrapefruit.platform.prompts;

class YesNoListener extends AbstractListener {
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
        if (ch == 'y' || ch == 'Y') {
            selected = true;
            finished = true;
        } else if (ch == 'n' || ch == 'N') {
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
