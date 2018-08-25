package net.rubygrapefruit.platform.test;

import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.TerminalInput;
import net.rubygrapefruit.platform.TerminalInputListener;
import net.rubygrapefruit.platform.Terminals;

import java.util.List;

public class Prompter {
    private final Terminals terminals;

    public Prompter(Terminals terminals) {
        this.terminals = terminals;
    }

    /**
     * Asks the user to select an option from a list. Returns the index of the selected option or a value < 0 on end of input.
     */
    public int select(String prompt, List<String> options, int defaultOption) {
        if (terminals.isTerminalInput() && terminals.isTerminal(Terminals.Output.Stdout)) {
            return selectInteractive(prompt, options, defaultOption);
        } else {
            return defaultOption;
        }
    }

    private int selectInteractive(String prompt, List<String> options, final int defaultOption) {
        Terminal output = terminals.getTerminal(Terminals.Output.Stdout);
        TerminalInput input = terminals.getTerminalInput();
        SelectView view = new SelectView(output, prompt, options, defaultOption);
        view.render();
        input.rawMode();
        SelectionListener listener = new SelectionListener(view);
        while (listener.selected == -1) {
            input.read(listener);
        }
        input.reset();
        view.selected = listener.selected;
        view.close();
        return listener.selected;
    }

    private static class SelectView {
        final Terminal output;
        final String prompt;
        final List<String> options;
        int selected;

        SelectView(Terminal output, String prompt, List<String> options, int defaultOption) {
            this.output = output;
            this.prompt = prompt;
            this.options = options;
            this.selected = defaultOption;
        }

        void render() {
            output.newLine();
            output.write(prompt).newLine();
            for (int i = 0; i < options.size(); i++) {
                renderItem(i);
            }
            output.foreground(Terminal.Color.White)
                    .write("  Use the arrow keys to select an option and press enter")
                    .reset()
                    .cursorStartOfLine();
        }

        private void renderItem(int i) {
            if (i == selected) {
                output.foreground(Terminal.Color.Cyan);
                output.write("> ");
            } else {
                output.write("  ");
            }
            output.write(String.valueOf((i + 1))).write(") ").write(options.get(i));
            output.reset();
            output.newLine();
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

        void close() {
            output.clearToEndOfLine();
            for (int i = 0; i < options.size(); i++) {
                output.cursorUp(1).clearToEndOfLine();
            }
            output.cursorUp(1);
            output.write(prompt)
                    .write(" ");
            if (selected >= 0) {
                output.foreground(Terminal.Color.Cyan)
                        .write(options.get(selected))
                        .reset();
            } else {
                output.write("<none>");
            }
            output.newLine();
        }
    }

    private static class SelectionListener implements TerminalInputListener {
        private final SelectView view;
        int selected;

        SelectionListener(SelectView view) {
            this.view = view;
            selected = -1;
        }

        @Override
        public void character(char ch) {
            if (Character.isDigit(ch)) {
                int index = ch - '0' - 1;
                if (index >= 0 && index < view.options.size()) {
                    this.selected = index;
                }
            }
        }

        @Override
        public void controlKey(Key key) {
            if (key == Key.Enter) {
                selected = view.selected;
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
}
