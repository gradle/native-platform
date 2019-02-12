package net.rubygrapefruit.platform.prompts;

import java.util.List;

abstract class AbstractPrompter {
    abstract boolean isInteractive();

    Integer select(String prompt, List<String> options, int defaultOption) {
        return defaultOption;
    }

    String enterText(String prompt, String defaultValue) {
        return defaultValue;
    }

    String enterPassword(String prompt) {
        return null;
    }

    Boolean askYesNo(String prompt, boolean defaultValue) {
        return defaultValue;
    }
}
