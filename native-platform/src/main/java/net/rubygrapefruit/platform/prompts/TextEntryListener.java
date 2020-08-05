package net.rubygrapefruit.platform.prompts;

class TextEntryListener extends AbstractListener {
    private final TextView view;
    private final String defaultValue;
    private String entered;
    private boolean finished;

    TextEntryListener(TextView view, String defaultValue) {
        this.view = view;
        this.defaultValue = defaultValue;
    }

    boolean isFinished() {
        return finished;
    }

    String getEntered() {
        return entered;
    }

    @Override
    public void character(char ch) {
        view.insert(ch);
    }

    @Override
    public void controlKey(Key key) {
        if (key == Key.Enter) {
            if (!view.hasValue()) {
                entered = defaultValue;
            } else {
                entered = view.getValue();
            }
            finished = true;
        } else if (key == Key.EraseBack) {
            view.eraseBack();
        } else if (key == Key.EraseForward) {
            view.eraseForward();
        } else if (key == Key.LeftArrow) {
            view.cursorLeft();
        } else if (key == Key.RightArrow) {
            view.cursorRight();
        } else if (key == Key.Home) {
            view.cursorStart();
        } else if (key == Key.End) {
            view.cursorEnd();
        }
    }

    @Override
    public void endInput() {
        finished = true;
    }
}
