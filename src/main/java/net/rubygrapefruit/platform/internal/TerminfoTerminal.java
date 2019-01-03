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
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;
import net.rubygrapefruit.platform.internal.jni.TerminfoFunctions;
import net.rubygrapefruit.platform.terminal.TerminalOutput;
import net.rubygrapefruit.platform.terminal.TerminalSize;
import net.rubygrapefruit.platform.terminal.Terminals;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class TerminfoTerminal extends AbstractTerminal {
    private final Terminals.Output output;
    private final TerminalCapabilities capabilities = new TerminalCapabilities();
    private final OutputStream outputStream;
    private final Object lock = new Object();
    private Map<Color, byte[]> foregroundColors = new HashMap<Color, byte[]>();
    private byte[] boldOn;
    private byte[] dim;
    private byte[] defaultForeground;
    private byte[] reset;
    private byte[] hideCursor;
    private byte[] showCursor;
    private byte[] up;
    private byte[] down;
    private byte[] left;
    private byte[] right;
    private byte[] startLine;
    private byte[] clearEOL;
    private Color foreground;
    private boolean ansiTerminal;
    private boolean bright;

    public TerminfoTerminal(Terminals.Output output) {
        this.output = output;
        this.outputStream = AbstractTerminal.streamForOutput(output);
    }

    @Override
    public String toString() {
        return String.format("Curses terminal %s on %s", capabilities.terminalName, getOutputDisplay());
    }

    private String getOutputDisplay() {
        return output.toString().toLowerCase();
    }

    @Override
    protected void init() {
        synchronized (lock) {
            FunctionResult result = new FunctionResult();
            TerminfoFunctions.initTerminal(output.ordinal(), capabilities, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not open terminal for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            ansiTerminal = isAnsiTerminal();
            hideCursor = TerminfoFunctions.hideCursor(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine hide cursor control sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            showCursor = TerminfoFunctions.showCursor(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine show cursor control sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            defaultForeground = TerminfoFunctions.defaultForeground(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine default foreground control sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            boldOn = TerminfoFunctions.boldOn(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine bold on control sequence %s: %s", getOutputDisplay(), result.getMessage()));
            }
            dim = TerminfoFunctions.dimOn(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine dim on control sequence %s: %s", getOutputDisplay(), result.getMessage()));
            }
            if (dim == null && ansiTerminal) {
                dim = AnsiTerminal.DIM_ON;
            }
            reset = TerminfoFunctions.reset(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine reset control sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            down = TerminfoFunctions.down(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine cursor down sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            up = TerminfoFunctions.up(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine cursor up sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            left = TerminfoFunctions.left(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine cursor left sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            right = TerminfoFunctions.right(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine cursor right sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            startLine = TerminfoFunctions.startLine(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine cursor to start of line sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            clearEOL = TerminfoFunctions.clearToEndOfLine(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not determine clear to end of line sequence for %s: %s", getOutputDisplay(), result.getMessage()));
            }
        }
    }

    private boolean isAnsiTerminal() {
        // A hard-coded (and very incomplete) list of terminals that are ANSI capable
        return capabilities.terminalName.contains("xterm") || capabilities.terminalName.equals("linux");
    }

    @Override
    public TerminalSize getTerminalSize() {
        synchronized (lock) {
            MutableTerminalSize terminalSize = new MutableTerminalSize();
            FunctionResult result = new FunctionResult();
            PosixTerminalFunctions.getTerminalSize(output.ordinal(), terminalSize, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not get terminal size for %s: %s", getOutputDisplay(), result.getMessage()));
            }
            return terminalSize;
        }
    }

    @Override
    public boolean supportsColor() {
        return getColor(Color.Black, false) != null;
    }

    @Override
    public boolean supportsCursorMotion() {
        return up != null && down != null && left != null && right != null && startLine != null;
    }

    @Override
    public boolean supportsTextAttributes() {
        return boldOn != null && dim != null;
    }

    @Override
    public boolean supportsCursorVisibility() {
        return showCursor != null && hideCursor != null;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public TerminalOutput foreground(Color color) {
        synchronized (lock) {
            byte[] sequence = getColor(color, bright);
            if (sequence != null) {
                write(sequence);
            }
            foreground = color;
        }
        return this;
    }

    private byte[] getColor(Color color, boolean bright) {
        if (bright && ansiTerminal) {
            return AnsiTerminal.BRIGHT_FOREGROUND.get(color.ordinal());
        }
        byte[] sequence = foregroundColors.get(color);
        if (sequence == null) {
            FunctionResult result = new FunctionResult();
            sequence = TerminfoFunctions.foreground(color.ordinal(), result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not switch foreground color for %s: %s", getOutputDisplay(),
                        result.getMessage()));
            }
            foregroundColors.put(color, sequence);
        }
        return sequence;
    }

    @Override
    public TerminalOutput bold() {
        if (!supportsTextAttributes()) {
            return this;
        }

        synchronized (lock) {
            write(boldOn);
        }
        return this;
    }

    @Override
    public TerminalOutput dim() throws NativeException {
        if (!supportsTextAttributes()) {
            return this;
        }

        synchronized (lock) {
            write(dim);
            if (bright && foreground != null) {
                write(getColor(foreground, false));
            }
            bright = false;
        }
        return this;
    }

    @Override
    public TerminalOutput bright() throws NativeException {
        synchronized (lock) {
            bright = true;
            if (foreground != null) {
                write(getColor(foreground, true));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput normal() {
        synchronized (lock) {
            if (reset != null) {
                write(reset);
            }
            bright = false;
            if (foreground != null) {
                write(getColor(foreground, bright));
            }
        }
        return this;
    }

    @Override
    public TerminalOutput defaultForeground() throws NativeException {
        synchronized (lock) {
            if (defaultForeground != null) {
                write(defaultForeground);
            }
            foreground = null;
        }
        return this;
    }

    @Override
    public TerminalOutput reset() {
        synchronized (lock) {
            if (reset != null) {
                write(reset);
            }
            if (showCursor != null) {
                write(showCursor);
            }
            bright = false;
            foreground = null;
        }
        return this;
    }

    @Override
    public TerminalOutput hideCursor() throws NativeException {
        if (!supportsCursorVisibility()) {
            return this;
        }

        synchronized (lock) {
            write(hideCursor);
        }
        return this;
    }

    @Override
    public TerminalOutput showCursor() throws NativeException {
        synchronized (lock) {
            if (showCursor != null) {
                write(showCursor);
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorDown(int count) {
        synchronized (lock) {
            if (down == null) {
                throw new NativeException(String.format("Cursor down not supported for %s", toString()));
            }
            for (int i = 0; i < count; i++) {
                write(down);
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorUp(int count) {
        synchronized (lock) {
            if (up == null) {
                throw new NativeException(String.format("Cursor up not supported for %s", toString()));
            }
            for (int i = 0; i < count; i++) {
                write(up);
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorLeft(int count) {
        synchronized (lock) {
            if (left == null) {
                throw new NativeException(String.format("Cursor left not supported for %s", toString()));
            }
            for (int i = 0; i < count; i++) {
                write(left);
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorRight(int count) {
        synchronized (lock) {
            if (right == null) {
                throw new NativeException(String.format("Cursor right not supported for %s", toString()));
            }
            for (int i = 0; i < count; i++) {
                write(right);
            }
        }
        return this;
    }

    @Override
    public TerminalOutput cursorStartOfLine() throws NativeException {
        synchronized (lock) {
            if (startLine == null) {
                throw new NativeException(String.format("Cursor to start of line not supported for %s", toString()));
            }
            write(startLine);
        }
        return this;
    }

    @Override
    public TerminalOutput clearToEndOfLine() throws NativeException {
        synchronized (lock) {
            if (clearEOL == null) {
                throw new NativeException(String.format("Clear to end of line not supported for %s", toString()));
            }
            write(clearEOL);
        }
        return this;
    }
}
