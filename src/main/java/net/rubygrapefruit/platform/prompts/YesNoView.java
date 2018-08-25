package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.TerminalOutput;

import static net.rubygrapefruit.platform.prompts.Prompter.DEFAULT_VALUE_COLOR;
import static net.rubygrapefruit.platform.prompts.Prompter.SELECTION_COLOR;

class YesNoView {
    private final TerminalOutput output;
    private final String prompt;
    private final boolean defaultValue;

    YesNoView(TerminalOutput output, String prompt, boolean defaultValue) {
        this.output = output;
        this.prompt = prompt;
        this.defaultValue = defaultValue;
    }

    public void render() {
        output.newline();
        output.hideCursor();
        output.bold().write(prompt).normal().write(" [yn]: ");
        output.foreground(DEFAULT_VALUE_COLOR).write(defaultValue ? "y" : "n").defaultForeground().cursorLeft(1);
        output.showCursor();
    }

    public void close(Boolean selected) {
        output.cursorStartOfLine();
        output.clearToEndOfLine();
        output.write(prompt).write(": ");
        if (selected != null) {
            output.foreground(SELECTION_COLOR);
            output.write(selected ? "yes" : "no");
            output.reset();
        } else {
            output.write("<none>");
        }
        output.newline();
    }
}
