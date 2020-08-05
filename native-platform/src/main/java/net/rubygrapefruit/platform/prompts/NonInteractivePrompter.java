package net.rubygrapefruit.platform.prompts;

class NonInteractivePrompter extends AbstractPrompter {
    @Override
    boolean isInteractive() {
        return false;
    }
}
