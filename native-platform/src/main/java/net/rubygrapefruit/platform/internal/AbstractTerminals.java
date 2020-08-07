/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.terminal.TerminalInput;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.Terminals;

public abstract class AbstractTerminals implements Terminals {
    private final Object lock = new Object();
    private Output currentlyOpen;
    private AbstractTerminal currentOutput;
    private TerminalInput currentInput;

    @Override
    public Terminals withAnsiOutput() {
        return new AnsiTerminals(this);
    }

    public TerminalOutput getTerminal(Output output) {
        synchronized (lock) {
            if (currentlyOpen != null && currentlyOpen != output) {
                throw new UnsupportedOperationException("Currently only one output can be used as a terminal.");
            }

            if (currentOutput == null) {
                final AbstractTerminal terminal = createTerminal(output);
                terminal.init();
                Runtime.getRuntime().addShutdownHook(new Thread(){
                    @Override
                    public void run() {
                        terminal.reset();
                    }
                });
                currentlyOpen = output;
                currentOutput = terminal;
            }

            return currentOutput;
        }
    }

    @Override
    public TerminalInput getTerminalInput() throws NativeException {
        synchronized (lock) {
            if (currentInput == null) {
                currentInput = createInput();
                Runtime.getRuntime().addShutdownHook(new Thread(){
                    @Override
                    public void run() {
                        currentInput.reset();
                    }
                });
            }
            return currentInput;
        }
    }

    protected abstract TerminalInput createInput();

    protected abstract AbstractTerminal createTerminal(Output output);

}
