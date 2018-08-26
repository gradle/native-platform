package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.terminal.TerminalOutput;

class PasswordView extends TextView {
    PasswordView(TerminalOutput output, String prompt) {
        super(output, prompt, "");
    }

    @Override
    protected int renderValue(CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            output.write('*');
        }
        return value.length();
    }

    @Override
    protected int renderFinalValue(CharSequence value) {
        output.write("[hidden]");
        return 8;
    }
}
