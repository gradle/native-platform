package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.util.List;

/**
 * Displays prompts on the terminal to ask the user various kinds of questions.
 */
public class Prompter {
    static final TerminalOutput.Color SELECTION_COLOR = TerminalOutput.Color.Cyan;
    private final boolean interactive;
    private final Terminals terminals;

    public Prompter(Terminals terminals) {
        interactive = terminals.isTerminalInput() && terminals.isTerminal(Terminals.Output.Stdout) && terminals.getTerminal(Terminals.Output.Stdout).supportsCursorMotion();
        this.terminals = terminals;
    }

    /**
     * Returns true if this prompter can ask the user questions.
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Asks the user to select an option from a list.
     *
     * @return The index of the selected option or null on end of input. Returns the default option when not interactive.
     */
    public Integer select(String prompt, List<String> options, int defaultOption) {
        if (interactive) {
            return selectInteractive(prompt, options, defaultOption);
        } else {
            return defaultOption;
        }
    }

    /**
     * Asks the user to enter some text.
     *
     * @return The text or null on end of input. Returns the default value when not interactive.
     */
    public String enterText(String prompt, String defaultValue) {
        if (interactive) {
            return enterTextInteractive(prompt, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Asks the user to enter a password.
     *
     * @return The password or null on end of input or when not interactive.
     */
    public String enterPassword(String prompt) {
        if (interactive) {
            return enterPasswordInteractive(prompt);
        } else {
            return null;
        }
    }

    /**
     * Asks the user a yes/no question.
     *
     * @return The selected value or null on end of input. Returns the default value when not interactive.
     */
    public Boolean askYesNo(String prompt, boolean defaultValue) {
        if (interactive) {
            return yesNoInteractive(prompt, defaultValue);
        } else {
            return defaultValue;
        }
    }

    private Boolean yesNoInteractive(String prompt, boolean defaultValue) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        YesNoView view = new YesNoView(output, prompt, defaultValue);
        YesNoListener listener = new YesNoListener(defaultValue);
        view.render();
        handleInput(listener);
        view.close(listener.getSelected());
        return listener.getSelected();
    }

    private Integer selectInteractive(String prompt, List<String> options, final int defaultOption) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        SelectView view = new SelectView(output, prompt, options, defaultOption);
        SelectionListener listener = new SelectionListener(view, options);
        view.render();
        handleInput(listener);
        view.close(listener.getSelected());
        return listener.getSelected();
    }

    private String enterTextInteractive(String prompt, String defaultValue) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        TextView view = new TextView(output, prompt, defaultValue);
        TextEntryListener listener = new TextEntryListener(view, defaultValue);
        view.render();
        handleInput(listener);
        view.close(listener.getEntered());
        return listener.getEntered();
    }

    private String enterPasswordInteractive(String prompt) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        PasswordView view = new PasswordView(output, prompt);
        TextEntryListener listener = new TextEntryListener(view, null);
        view.render();
        handleInput(listener);
        view.close(listener.getEntered());
        return listener.getEntered();
    }

    private void handleInput(AbstractListener listener) {
        TerminalInput input = terminals.getTerminalInput();
        input.rawMode();
        try {
            while (!listener.isFinished()) {
                input.read(listener);
            }
        } finally {
            input.reset();
        }
    }
}
