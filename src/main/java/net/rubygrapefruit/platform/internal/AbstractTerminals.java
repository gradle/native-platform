package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.Terminals;

public abstract class AbstractTerminals implements Terminals {
    private final Object lock = new Object();
    private Output currentlyOpen;
    private AbstractTerminal current;

    public Terminal getTerminal(Output output) {
        synchronized (lock) {
            if (currentlyOpen != null && currentlyOpen != output) {
                throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
            }

            if (current == null) {
                final AbstractTerminal terminal = createTerminal(output);
                terminal.init();
                Runtime.getRuntime().addShutdownHook(new Thread(){
                    @Override
                    public void run() {
                        terminal.reset();
                    }
                });
                currentlyOpen = output;
                current = terminal;
            }

            return current;
        }
    }

    protected abstract AbstractTerminal createTerminal(Output output);
}
