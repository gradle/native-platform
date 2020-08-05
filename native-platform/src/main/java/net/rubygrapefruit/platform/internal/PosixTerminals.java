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
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;
import net.rubygrapefruit.platform.internal.jni.PosixTerminalFunctions;

public class PosixTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        switch (output) {
            case Stdout:
                return PosixTerminalFunctions.isatty(NativeLibraryFunctions.STDOUT);
            case Stderr:
                return PosixTerminalFunctions.isatty(NativeLibraryFunctions.STDERR);
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public boolean isTerminalInput() throws NativeException {
        return PosixTerminalFunctions.isatty(NativeLibraryFunctions.STDIN);
    }

    @Override
    protected TerminalInput createInput() {
        return new PosixTerminalInput();
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        return new TerminfoTerminal(output);
    }
}
