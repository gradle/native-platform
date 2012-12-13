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
import net.rubygrapefruit.platform.TerminalSize;
import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;

public class TerminfoTerminal extends AbstractTerminal {
    private final Terminals.Output output;
    private final TerminalCapabilities capabilities = new TerminalCapabilities();
    private Color foreground;

    public TerminfoTerminal(Terminals.Output output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return String.format("Curses terminal on %s", getOutputDisplay());
    }

    private String getOutputDisplay() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void init() {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.initTerminal(output.ordinal(), capabilities, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not open terminal for %s: %s", getOutputDisplay(), result.getMessage()));
        }
    }

    public TerminalSize getTerminalSize() {
        MutableTerminalSize terminalSize = new MutableTerminalSize();
        FunctionResult result = new FunctionResult();
        PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get terminal size for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return terminalSize;
    }

    public boolean supportsColor() {
        return capabilities.colors;
    }

    public boolean supportsCursorMotion() {
        return capabilities.cursorMotion;
    }

    public boolean supportsTextAttributes() {
        return capabilities.textAttributes;
    }

    public Terminal foreground(Color color) {
        if (!capabilities.colors) {
            return this;
        }

        FunctionResult result = new FunctionResult();
        TerminfoFunctions.foreground(color.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch foreground color for %s: %s", getOutputDisplay(),
                    result.getMessage()));
        }
        foreground = color;
        return this;
    }

    public Terminal bold() {
        if (!capabilities.textAttributes) {
            return this;
        }

        FunctionResult result = new FunctionResult();
        TerminfoFunctions.bold(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not switch to bold mode for %s: %s", getOutputDisplay(),
                    result.getMessage()));
        }
        return this;
    }

    public Terminal normal() {
        reset();
        if (foreground != null) {
            foreground(foreground);
        }
        return this;
    }

    public Terminal reset() {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.reset(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not reset terminal for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorDown(int count) {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.down(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor down for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorUp(int count) {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.up(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor up for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorLeft(int count) {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.left(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor left for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorRight(int count) {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.right(count, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor right for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal cursorStartOfLine() throws NativeException {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.startLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not move cursor to start of line for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }

    public Terminal clearToEndOfLine() throws NativeException {
        FunctionResult result = new FunctionResult();
        TerminfoFunctions.clearToEndOfLine(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not clear to end of line for %s: %s", getOutputDisplay(), result.getMessage()));
        }
        return this;
    }
}
