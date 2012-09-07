package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.Terminals;

public abstract class AbstractTerminals implements Terminals {
    private static Output currentlyOpen;
    private static AbstractTerminal current;

    public Terminal getTerminal(Output output) {
        synchronized (AbstractTerminals.class) {
            if (currentlyOpen != null && currentlyOpen != output) {
                throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
            }

            if (current == null) {
                AbstractTerminal terminal = createTerminal(output);
                terminal.init();
                currentlyOpen = output;
                current = terminal;
            }

            return current;
        }
    }

    protected abstract AbstractTerminal createTerminal(Output output);
}
