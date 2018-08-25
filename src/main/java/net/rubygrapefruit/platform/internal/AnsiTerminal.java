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

import java.io.IOException;
import java.io.OutputStream;

public class AnsiTerminal extends AbstractTerminal {
    private static final byte[] BOLD = "\u001b[1m".getBytes();
    private static final byte[] BOLD_OFF = "\u001b[21m".getBytes();
    private static final byte[] DEFAULT_FG = "\u001b[39m".getBytes();
    private static final byte[] RESET = "\u001b[0m".getBytes();
    private static final byte[] START_OF_LINE = "\u001b[0E".getBytes();
    private static final byte[] CLEAR_TO_END_OF_LINE = "\u001b[0K".getBytes();
    private final Terminals.Output output;
    private final OutputStream outputStream;

    public AnsiTerminal(OutputStream outputStream, Terminals.Output output) {
        this.outputStream = outputStream;
        this.output = output;
    }

    @Override
    public String toString() {
        return String.format("ANSI terminal on %s", getOutputDisplay());
    }

    private String getOutputDisplay() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void init() {
    }

    public boolean supportsTextAttributes() {
        return true;
    }

    public boolean supportsColor() {
        return true;
    }

    public boolean supportsCursorMotion() {
        return true;
    }

    @Override
    public boolean supportsCursorVisibility() {
        return false;
    }

    public TerminalSize getTerminalSize() throws NativeException {
        return new MutableTerminalSize();
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public Terminal foreground(Color color) throws NativeException {
        try {
            String esc = String.format("\u001b[%sm", 30 + color.ordinal());
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not set foreground color on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal bold() throws NativeException {
        try {
            outputStream.write(BOLD);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to bold output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public Terminal defaultForeground() throws NativeException {
        try {
            outputStream.write(DEFAULT_FG);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to bold output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal normal() throws NativeException {
        try {
            outputStream.write(BOLD_OFF);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to normal output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal reset() throws NativeException {
        try {
            outputStream.write(RESET);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not reset output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public Terminal hideCursor() throws NativeException {
        return this;
    }

    @Override
    public Terminal showCursor() throws NativeException {
        return this;
    }

    public Terminal cursorLeft(int count) throws NativeException {
        try {
            String esc = String.format("\u001b[%sD", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal cursorRight(int count) throws NativeException {
        try {
            String esc = String.format("\u001b[%sC", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal cursorUp(int count) throws NativeException {
        try {
            String esc = String.format("\u001b[%sA", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal cursorDown(int count) throws NativeException {
        try {
            String esc = String.format("\u001b[%sB", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal cursorStartOfLine() throws NativeException {
        try {
            outputStream.write(START_OF_LINE);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public Terminal clearToEndOfLine() throws NativeException {
        try {
            outputStream.write(CLEAR_TO_END_OF_LINE);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not clear to end of line on %s.", getOutputDisplay()), e);
        }
        return this;
    }
}
