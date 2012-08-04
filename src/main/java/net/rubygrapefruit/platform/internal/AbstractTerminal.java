package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.Terminal;

public abstract class AbstractTerminal implements Terminal {
    public final void init() {
        doInit();
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                reset();
            }
        });
    }

    protected abstract void doInit();
}
