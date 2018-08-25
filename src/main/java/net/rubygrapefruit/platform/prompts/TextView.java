package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.TerminalOutput;

class TextView {
    private final TerminalOutput output;
    private final String prompt;
    private final String defaultValue;
    private StringBuilder value = new StringBuilder();
    private int insertPos = 0;
    private int cursor = 0;

    TextView(TerminalOutput output, String prompt, String defaultValue) {
        this.output = output;
        this.prompt = prompt;
        this.defaultValue = defaultValue;
    }

    boolean hasValue() {
        return value.length() > 0;
    }

    String getValue() {
        return value.toString();
    }

    void render() {
        output.newline();
        output.hideCursor();
        output.bold().write(prompt).write(": ").normal();
        output.foreground(Prompter.DEFAULT_VALUE_COLOR);
        output.write(defaultValue);
        output.cursorLeft(defaultValue.length());
        output.reset();
    }

    void update() {
        output.hideCursor();
        output.cursorLeft(cursor);
        output.clearToEndOfLine();
        if (value.length() == 0) {
            output.foreground(Prompter.DEFAULT_VALUE_COLOR);
            output.write(defaultValue);
            output.cursorLeft(defaultValue.length() - insertPos);
        } else {
            output.foreground(Prompter.SELECTION_COLOR);
            output.write(value.toString());
            output.cursorLeft(value.length() - insertPos);
        }
        output.reset();
        cursor = insertPos;
    }

    void insert(char ch) {
        value.insert(insertPos, ch);
        insertPos++;
        update();
    }

    void eraseBack() {
        if (insertPos == 0) {
            return;
        }
        value.deleteCharAt(insertPos - 1);
        insertPos--;
        update();
    }

    void eraseForward() {
        if (insertPos == value.length()) {
            return;
        }
        value.deleteCharAt(insertPos);
        update();
    }

    void cursorStart() {
        insertPos = 0;
        output.cursorLeft(cursor);
        cursor = 0;
    }

    void cursorEnd() {
        insertPos = value.length();
        output.cursorRight(insertPos - cursor);
        cursor = insertPos;
    }

    void cursorLeft() {
        if (insertPos == 0) {
            return;
        }
        insertPos--;
        cursor--;
        output.cursorLeft(1);
    }

    void cursorRight() {
        if (insertPos == value.length()) {
            return;
        }
        insertPos++;
        cursor++;
        output.cursorRight(1);
    }

    void close(String entered) {
        output.cursorStartOfLine();
        output.clearToEndOfLine();
        output.write(prompt).write(": ");
        if (entered != null) {
            output.foreground(Prompter.SELECTION_COLOR);
            output.write(entered);
            output.reset();
        } else {
            output.write("<none>");
        }
        output.newline();
    }
}
