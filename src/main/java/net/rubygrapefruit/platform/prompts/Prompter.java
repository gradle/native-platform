package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.util.List;

/**
 * Displays prompts on the terminal to ask the user various kinds of questions.
 */
public class Prompter {
    static final TerminalOutput.Color SELECTION_COLOR = TerminalOutput.Color.Cyan;
    private final AbstractPrompter implementation;

    public Prompter(Terminals terminals) {
        if (!terminals.isTerminalInput() || !terminals.isTerminal(Terminals.Output.Stdout)) {
            implementation = new NonInteractivePrompter();
        } else {
            if (terminals.getTerminal(Terminals.Output.Stdout).supportsCursorMotion() && terminals.getTerminalInput().supportsRawMode()) {
                implementation = new InteractivePrompter(terminals);
            } else {
                implementation = new PlainPrompter(terminals);
            }
        }
    }

    /**
     * Returns {@code true} if this prompter can ask the user questions.
     */
    public boolean isInteractive() {
        return implementation.isInteractive();
    }

    /**
     * Asks the user to select an option from a list.
     *
     * @return The index of the selected option or {@code null} on end of input. Returns the default option when not interactive.
     */
    public Integer select(String prompt, List<String> options, int defaultOption) {
        return implementation.select(prompt, options, defaultOption);
    }

    /**
     * Asks the user to enter some text.
     *
     * @return The text or {@code null} on end of input. Returns the default value when not interactive.
     */
    public String enterText(String prompt, String defaultValue) {
        return implementation.enterText(prompt, defaultValue);
    }

    /**
     * Asks the user to enter a password.
     *
     * @return The password or {@code null} on end of input or when not interactive or when not possible to prompt for a password without echoing the characters.
     */
    public String enterPassword(String prompt) {
        return implementation.enterPassword(prompt);
    }

    /**
     * Asks the user a yes/no question.
     *
     * @return The selected value or null on end of input. Returns the default value when not interactive.
     */
    public Boolean askYesNo(String prompt, boolean defaultValue) {
        return implementation.askYesNo(prompt, defaultValue);
    }
}
