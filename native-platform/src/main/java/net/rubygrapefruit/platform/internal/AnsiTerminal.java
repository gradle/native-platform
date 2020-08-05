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
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.TerminalSize;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AnsiTerminal extends AbstractTerminal {
    private static final byte[] BOLD = "\u001b[1m".getBytes();
    static final byte[] DIM_ON = "\u001b[2m".getBytes();
    private static final byte[] NORMAL_INTENSITY = "\u001b[22m".getBytes();
    private static final byte[] DEFAULT_FG = "\u001b[39m".getBytes();
    private static final byte[] RESET = "\u001b[0m".getBytes();
    private static final byte[] START_OF_LINE = "\u001b[1G".getBytes();
    private static final byte[] CLEAR_TO_END_OF_LINE = "\u001b[0K".getBytes();
    private static final List<byte[]> FOREGROUND = new ArrayList<byte[]>();
    static final List<byte[]> BRIGHT_FOREGROUND = new ArrayList<byte[]>();
    private Color foreground;
    private boolean bright;
    private final Terminals.Output output;
    private final OutputStream outputStream;

    static {
        for (Color color : Color.values()) {
            FOREGROUND.add(String.format("\u001b[%sm", 30 + color.ordinal()).getBytes());
            BRIGHT_FOREGROUND.add(String.format("\u001b[%sm", 90 + color.ordinal()).getBytes());
        }
    }

    public AnsiTerminal(OutputStream outputStream, Terminals.Output output) {
        this.outputStream = outputStream;
        this.output = output;
    }

    public AnsiTerminal(Terminals.Output output) {
        this(streamForOutput(output), output);
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

    public TerminalOutput foreground(Color color) throws NativeException {
        try {
            if (bright) {
                outputStream.write(BRIGHT_FOREGROUND.get(color.ordinal()));
            } else {
                outputStream.write(FOREGROUND.get(color.ordinal()));
            }
            foreground = color;
        } catch (IOException e) {
            throw new NativeException(String.format("Could not set foreground color on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public TerminalOutput defaultForeground() throws NativeException {
        try {
            outputStream.write(DEFAULT_FG);
            foreground = null;
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to bold output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public TerminalOutput bright() throws NativeException {
        try {
            bright = true;
            if (foreground != null) {
                outputStream.write(BRIGHT_FOREGROUND.get(foreground.ordinal()));
            }
        } catch (IOException e) {
            throw new NativeException(String.format("Could not set foreground color on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public TerminalOutput dim() throws NativeException {
        try {
            outputStream.write(DIM_ON);
            bright = false;
        } catch (IOException e) {
            throw new NativeException(String.format("Could not set foreground color on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput bold() throws NativeException {
        try {
            outputStream.write(BOLD);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to bold output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput normal() throws NativeException {
        try {
            outputStream.write(NORMAL_INTENSITY);
            if (foreground != null && bright) {
                outputStream.write(FOREGROUND.get(foreground.ordinal()));
            }
            bright = false;
        } catch (IOException e) {
            throw new NativeException(String.format("Could not switch to normal output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput reset() throws NativeException {
        try {
            outputStream.write(RESET);
            foreground = null;
            bright = false;
        } catch (IOException e) {
            throw new NativeException(String.format("Could not reset output on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    @Override
    public TerminalOutput hideCursor() throws NativeException {
        return this;
    }

    @Override
    public TerminalOutput showCursor() throws NativeException {
        return this;
    }

    public TerminalOutput cursorLeft(int count) throws NativeException {
        if (count == 0) {
            return this;
        }
        try {
            String esc = String.format("\u001b[%sD", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput cursorRight(int count) throws NativeException {
        if (count == 0) {
            return this;
        }
        try {
            String esc = String.format("\u001b[%sC", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput cursorUp(int count) throws NativeException {
        if (count == 0) {
            return this;
        }
        try {
            String esc = String.format("\u001b[%sA", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput cursorDown(int count) throws NativeException {
        if (count == 0) {
            return this;
        }
        try {
            String esc = String.format("\u001b[%sB", count);
            outputStream.write(esc.getBytes());
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput cursorStartOfLine() throws NativeException {
        try {
            outputStream.write(START_OF_LINE);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not move cursor on %s.", getOutputDisplay()), e);
        }
        return this;
    }

    public TerminalOutput clearToEndOfLine() throws NativeException {
        try {
            outputStream.write(CLEAR_TO_END_OF_LINE);
        } catch (IOException e) {
            throw new NativeException(String.format("Could not clear to end of line on %s.", getOutputDisplay()), e);
        }
        return this;
    }
}
