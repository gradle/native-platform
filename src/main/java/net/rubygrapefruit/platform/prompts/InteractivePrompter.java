package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.util.List;

class InteractivePrompter extends AbstractPrompter {
    private final Terminals terminals;

    InteractivePrompter(Terminals terminals) {
        this.terminals = terminals;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    @Override
    Integer select(String prompt, List<String> options, int defaultOption) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        SelectView view = new SelectView(output, prompt, options, defaultOption);
        SelectionListener listener = new SelectionListener(view, options);
        view.render();
        handleInput(listener);
        view.close(listener.getSelected());
        return listener.getSelected();
    }

    @Override
    String enterText(String prompt, String defaultValue) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        TextView view = new TextView(output, prompt, defaultValue);
        TextEntryListener listener = new TextEntryListener(view, defaultValue);
        view.render();
        handleInput(listener);
        view.close(listener.getEntered());
        return listener.getEntered();
    }

    @Override
    String enterPassword(String prompt) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        PasswordView view = new PasswordView(output, prompt);
        TextEntryListener listener = new TextEntryListener(view, null);
        view.render();
        handleInput(listener);
        view.close(listener.getEntered());
        return listener.getEntered();
    }

    @Override
    Boolean askYesNo(String prompt, boolean defaultValue) {
        TerminalOutput output = terminals.getTerminal(Terminals.Output.Stdout);
        YesNoView view = new YesNoView(output, prompt, defaultValue);
        YesNoListener listener = new YesNoListener(defaultValue);
        view.render();
        handleInput(listener);
        view.close(listener.getSelected());
        return listener.getSelected();
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
