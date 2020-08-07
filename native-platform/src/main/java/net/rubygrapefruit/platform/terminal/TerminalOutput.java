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

package net.rubygrapefruit.platform.terminal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.OutputStream;

/**
 * Allows output to be written to the terminal/console.
 *
 * <p>On UNIX based platforms, this provides access to the terminal. On Windows platforms, this provides access to the
 * console.
 *
 * <p>To create an instance of this interface use the {@link Terminals#getTerminal(Terminals.Output)} method.
 */
@ThreadSafe
public interface TerminalOutput {
    /**
     * Basic colors supported by a terminal.
     */
    enum Color {
        // Don't change the order of these. They are in ANSI order
        Black, Red, Green, Yellow, Blue, Magenta, Cyan, White
    }

    /**
     * Returns true if this terminal supports setting text attributes, such as bold.
     */
    boolean supportsTextAttributes();

    /**
     * Returns true if this terminal supports setting output colors.
     */
    boolean supportsColor();

    /**
     * Returns true if this terminal supports moving the cursor.
     */
    boolean supportsCursorMotion();

    /**
     * Returns true if this terminal supports hiding the cursor.
     */
    boolean supportsCursorVisibility();

    /**
     * Returns the size of the terminal. Supported by all terminals.
     *
     * @return The current terminal size. Never returns null.
     * @throws NativeException On failure.
     */
    TerminalSize getTerminalSize() throws NativeException;

    /**
     * Returns an {@link OutputStream} that writes to this terminal. The output stream is not buffered.
     */
    OutputStream getOutputStream();

    /**
     * Writes some text to this terminal. Uses the system encoding.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput write(CharSequence text) throws NativeException;

    /**
     * Writes a character to this terminal. Uses the system encoding.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput write(char ch) throws NativeException;

    /**
     * Writes line separator.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput newline() throws NativeException;

    /**
     * Sets the terminal foreground color, if supported. Does nothing if this terminal does not support setting the
     * foreground color.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput foreground(Color color) throws NativeException;

    /**
     * Sets the terminal foreground color to the default, if supported. Does nothing if this terminal does not support setting the
     * foreground color.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput defaultForeground() throws NativeException;

    /**
     * Switches the terminal to bold text mode, if supported. Does nothing if this terminal does not support bold mode.
     *
     * <p>Often the same as {@link #bright()}.</p>
     *
     * @throws NativeException On failure.
     */
    TerminalOutput bold() throws NativeException;

    /**
     * Switches the terminal to dim intensity, if supported. Does nothing if this terminal does not support dim mode.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput dim() throws NativeException;

    /**
     * Switches the terminal to high intensity, if supported. Does nothing if this terminal does not support bright mode.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput bright() throws NativeException;

    /**
     * Switches the terminal to normal text mode. Switches off bold text mode and sets normal intensity. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput normal() throws NativeException;

    /**
     * Resets this terminal. Switches to normal text mode, restores default foreground color and shows the cursor. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput reset() throws NativeException;

    /**
     * Hides the cursor. Does nothing if this terminal does not support hiding the cursor.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput hideCursor() throws NativeException;

    /**
     * Shows the cursor, if hidden. Supported by all terminals.
     *
     * @throws NativeException On failure.
     */
    TerminalOutput showCursor() throws NativeException;

    /**
     * Moves the cursor the given number of characters to the left.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    TerminalOutput cursorLeft(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters to the right.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    TerminalOutput cursorRight(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters up.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    TerminalOutput cursorUp(int count) throws NativeException;

    /**
     * Moves the cursor the given number of characters down.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    TerminalOutput cursorDown(int count) throws NativeException;

    /**
     * Moves the cursor to the start of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support cursor motion.
     */
    TerminalOutput cursorStartOfLine() throws NativeException;

    /**
     * Clears characters from the cursor position to the end of the current line.
     *
     * @throws NativeException On failure, or if this terminal does not support clearing.
     */
    TerminalOutput clearToEndOfLine() throws NativeException;
}
