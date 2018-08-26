package net.rubygrapefruit.platform.prompts;

import net.rubygrapefruit.platform.TerminalInputListener;

abstract class AbstractListener implements TerminalInputListener {
    abstract boolean isFinished();
}
