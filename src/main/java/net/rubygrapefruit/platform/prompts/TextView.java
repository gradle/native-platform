package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalOutput;

class TextView {
    final TerminalOutput output;
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
        output.dim();
        output.write(defaultValue);
        output.cursorLeft(defaultValue.length());
        output.reset();
    }

    void update() {
        output.hideCursor();
        output.cursorLeft(cursor);
        if (value.length() == 0) {
            output.dim();
            output.write(defaultValue);
            output.clearToEndOfLine();
            output.cursorLeft(defaultValue.length() - insertPos);
        } else {
            output.foreground(Prompter.SELECTION_COLOR);
            int len = renderValue(value);
            output.clearToEndOfLine();
            output.cursorLeft(len - insertPos);
        }
        output.reset();
        cursor = insertPos;
    }

    protected int renderValue(CharSequence value) {
        output.write(value);
        return value.length();
    }

    protected int renderFinalValue(CharSequence value) {
        return renderValue(value);
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
        output.write(prompt).write(": ");
        if (entered != null) {
            output.foreground(Prompter.SELECTION_COLOR);
            renderFinalValue(entered);
            output.reset();
        } else {
            output.write("<none>");
        }
        output.clearToEndOfLine();
        output.newline();
    }
}
