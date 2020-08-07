package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalInputListener;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.util.List;

class PlainPrompter extends AbstractPrompter {
    private final TerminalOutput output;
    private final TerminalInput input;

    PlainPrompter(Terminals terminals) {
        this.output = terminals.getTerminal(Terminals.Output.Stdout);
        this.input = terminals.getTerminalInput();
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    @Override
    Integer select(String prompt, List<String> options, int defaultOption) {
        output.newline();
        output.bold().write(prompt).write(":").normal().newline();
        for (int i = 0; i < options.size(); i++) {
            if (i == defaultOption) {
                output.foreground(Prompter.SELECTION_COLOR).write(">").defaultForeground().write(" ");
            } else {
                output.write("  ");
            }
            output.write(String.valueOf((i + 1))).write(") ").write(options.get(i));
            if (i == defaultOption) {
                output.foreground(Prompter.SELECTION_COLOR).write(" (default)").defaultForeground();
            }
            output.newline();
        }
        while (true) {
            output.write("Please select an option (1..").write(String.valueOf(options.size())).write(") [").write(String.valueOf(defaultOption + 1)).write("] ");
            CollectingListener listener = new CollectingListener();
            while (listener.result == 0) {
                input.read(listener);
            }
            if (listener.result == 2) {
                return null;
            }
            String result = listener.chars.toString().trim();
            if (result.length() == 0) {
                return defaultOption;
            }
            if (result.length() == 1 && Character.isDigit(result.charAt(0))) {
                char ch = result.charAt(0);
                int index = ch - '0' - 1;
                if (index >= 0 && index < options.size()) {
                    return index;
                }
            }
        }
    }

    @Override
    String enterText(String prompt, String defaultValue) {
        output.newline();
        output.bold().write(prompt).normal();
        output.write(" [").write(defaultValue).write("]: ");
        CollectingListener listener = new CollectingListener();
        while (listener.result == 0) {
            input.read(listener);
        }
        if (listener.result == 2) {
            return null;
        }
        String result = listener.chars.toString().trim();
        if (result.length() == 0) {
            return defaultValue;
        }
        return result;
    }

    @Override
    Boolean askYesNo(String prompt, boolean defaultValue) {
        output.newline();
        while (true) {
            output.bold().write(prompt).normal();
            output.write("(y/n) [").write(defaultValue ? "y" : "n").write("]: ");
            CollectingListener listener = new CollectingListener();
            while (listener.result == 0) {
                input.read(listener);
            }
            if (listener.result == 2) {
                return null;
            }
            String result = listener.chars.toString().trim().toLowerCase();
            if (result.length() == 0) {
                return defaultValue;
            }
            if (result.equals("y") || result.equals("yes")) {
                return true;
            }
            if (result.equals("n") || result.equals("no")) {
                return false;
            }
        }
    }

    private static class CollectingListener implements TerminalInputListener {
        int result = 0;
        final StringBuilder chars = new StringBuilder();

        @Override
        public void character(char ch) {
            chars.append(ch);
        }

        @Override
        public void controlKey(Key key) {
            if (key == Key.Enter) {
                result = 1;
            }
        }

        @Override
        public void endInput() {
            result = 2;
        }
    }
}
