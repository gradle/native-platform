package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalOutput;

import java.util.List;

class SelectView {
    private final TerminalOutput output;
    private final String prompt;
    private final List<String> options;
    private int selected;

    SelectView(TerminalOutput output, String prompt, List<String> options, int defaultOption) {
        this.output = output;
        this.prompt = prompt;
        this.options = options;
        this.selected = defaultOption;
    }

    int getSelected() {
        return selected;
    }

    void render() {
        output.newline();
        output.hideCursor();
        output.bold().write(prompt).write(":").normal().newline();
        for (int i = 0; i < options.size(); i++) {
            renderItem(i);
        }
        output.dim()
                .write("Use the arrow keys to select an option and press enter")
                .normal()
                .cursorStartOfLine();
    }

    private void renderItem(int i) {
        if (i == selected) {
            output.foreground(Prompter.SELECTION_COLOR);
            output.write("> ");
        } else {
            output.write("  ");
        }
        output.write(String.valueOf((i + 1))).write(") ").write(options.get(i));
        output.defaultForeground();
        output.newline();
    }

    void selectPrevious() {
        if (selected == 0) {
            return;
        }
        selected--;
        int rowsToMoveUp = options.size() - selected;
        output.cursorUp(rowsToMoveUp);
        renderItem(selected);
        renderItem(selected + 1);
        output.cursorDown(rowsToMoveUp - 2);
    }

    void selectNext() {
        if (selected == options.size() - 1) {
            return;
        }
        selected++;
        int rowsToModeUp = options.size() - selected + 1;
        output.cursorUp(rowsToModeUp);
        renderItem(selected - 1);
        renderItem(selected);
        output.cursorDown(rowsToModeUp - 2);
    }

    void close(Integer selected) {
        output.clearToEndOfLine();
        for (int i = 0; i < options.size(); i++) {
            output.cursorUp(1).clearToEndOfLine();
        }
        output.cursorUp(1);
        output.write(prompt)
                .write(": ");
        if (selected != null) {
            output.foreground(Prompter.SELECTION_COLOR)
                    .write(options.get(selected))
                    .reset();
        } else {
            output.write("<none>");
        }
        output.showCursor();
        output.newline();
    }
}
