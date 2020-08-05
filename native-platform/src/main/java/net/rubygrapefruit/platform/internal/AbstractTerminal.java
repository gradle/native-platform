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
import net.rubygrapefruit.platform.terminal.Terminals;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractTerminal implements TerminalOutput {
    protected static byte[] NEW_LINE = System.getProperty("line.separator").getBytes();

    protected abstract void init();

    protected static OutputStream streamForOutput(Terminals.Output output) {
        return output == Terminals.Output.Stdout ? new FileOutputStream(FileDescriptor.out) : new FileOutputStream(FileDescriptor.err);
    }

    @Override
    public TerminalOutput newline() throws NativeException {
        write(NEW_LINE);
        return this;
    }

    public TerminalOutput write(CharSequence text) throws NativeException {
        // TODO encode directly to output stream instead of creating intermediate String
        byte[] bytes = text.toString().getBytes();
        write(bytes);
        return this;
    }

    @Override
    public TerminalOutput write(char ch) throws NativeException {
        // TODO encode directly to output stream instead of creating intermediate String
        write(Character.toString(ch));
        return this;
    }

    protected void write(byte[] bytes) {
        try {
            getOutputStream().write(bytes);
        } catch (IOException e) {
            throw new NativeException("Could not write to output stream.", e);
        }
    }
}
