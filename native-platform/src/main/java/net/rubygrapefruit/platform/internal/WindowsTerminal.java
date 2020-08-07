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
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.TerminalSize;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.io.OutputStream;

public class WindowsTerminal extends AbstractTerminal {
    private final Object lock = new Object();
    private final Terminals.Output output;
    private final OutputStream outputStream;

    public WindowsTerminal(Terminals.Output output) {
        this.output = output;
        this.outputStream = streamForOutput(output);
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
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.initConsole(output.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not open console for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
    }

    @Override
    public boolean supportsColor() {
        return true;
    }

    @Override
    public boolean supportsTextAttributes() {
        return true;
    }

    @Override
    public boolean supportsCursorMotion() {
        return true;
    }

    @Override
    public boolean supportsCursorVisibility() {
        return true;
    }

    @Override
    public TerminalSize getTerminalSize() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            MutableTerminalSize size = new MutableTerminalSize();
            WindowsConsoleFunctions.getConsoleSize(output.ordinal(), size, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine console size for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            return size;
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public TerminalOutput bold() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.boldOn(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch console to bold mode for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput foreground(Color color) {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.foreground(color.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not change console foreground color for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput dim() throws NativeException {
        return normal();
    }

    @Override
    public TerminalOutput bright() throws NativeException {
        return bold();
    }

    @Override
    public TerminalOutput defaultForeground() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.defaultForeground(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch console to default foreground for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput normal() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.boldOff(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch console to normal mode for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput reset() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.reset(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not reset console for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput hideCursor() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.hideCursor(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not hide cursor for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput showCursor() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.showCursor(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not show cursor for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorDown(int count) throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.down(count, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not move cursor down for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorUp(int count) throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.up(count, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not move cursor up for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorLeft(int count) throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.left(count, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not move cursor left for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorRight(int count) throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.right(count, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not move cursor right for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorStartOfLine() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.startLine(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not move cursor to start of line for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput clearToEndOfLine() throws NativeException {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            WindowsConsoleFunctions.clearToEndOfLine(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could clear to end of line for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
        return this;
    }
}
