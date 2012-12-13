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
import net.rubygrapefruit.platform.Terminal;
import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

public class WindowsTerminal extends AbstractTerminal {
    private final Terminals.Output output;

    public WindowsTerminal(Terminals.Output output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return String.format("Windows console on %s", getOutputDisplay());
    }

    private String getOutputDisplay() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void init() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.initConsole(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open console for %s: %s", getOutputDisplay(), result.getMessage()));
        }
    }

    public boolean supportsColor() {
        return true;
    }

    public boolean supportsTextAttributes() {
        return true;
    }

    public boolean supportsCursorMotion() {
        return true;
    }

    public TerminalSize getTerminalSize() {
        FunctionResult result = new FunctionResult();
        MutableTerminalSize size = new MutableTerminalSize();
        WindowsConsoleFunctions.getConsoleSize(output.ordinal(), size, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine console size for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return size;
    }

    public Terminal bold() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.bold(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch console to bold mode for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal foreground(Color color) {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not change console foreground color for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal normal() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.normal(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch console to normal mode for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal reset() {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.reset(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not reset console for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorDown(int count) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.down(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor down for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorUp(int count) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.up(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor up for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorLeft(int count) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.left(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor left for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorRight(int count) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.right(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor right for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorStartOfLine() throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.startLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor to start of line for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal clearToEndOfLine() throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsConsoleFunctions.clearToEndOfLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could clear to end of line for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }
}
