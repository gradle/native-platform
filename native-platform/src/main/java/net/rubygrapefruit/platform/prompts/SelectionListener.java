package net.rubygrapefruit.platform.prompts;

import java.util.List;

class SelectionListener extends AbstractListener {
    private final SelectView view;
    private final List<String> options;
    private int selected;

    SelectionListener(SelectView view, List<String> options) {
        this.view = view;
        this.options = options;
        selected = -1;
    }

    boolean isFinished() {
        return selected != -1;
    }

    Integer getSelected() {
        return selected < 0 ? null : selected;
    }

    @Override
    public void character(char ch) {
        if (Character.isDigit(ch)) {
            int index = ch - '0' - 1;
            if (index >= 0 && index < options.size()) {
                this.selected = index;
            }
        }
    }

    @Override
    public void controlKey(Key key) {
        if (key == Key.Enter) {
            selected = view.getSelected();
        } else if (key == Key.UpArrow) {
            view.selectPrevious();
        } else if (key == Key.DownArrow) {
            view.selectNext();
        }
    }

    @Override
    public void endInput() {
        selected = -2;
    }
}
