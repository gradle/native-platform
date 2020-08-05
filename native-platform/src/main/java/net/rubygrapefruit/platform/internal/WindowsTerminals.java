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
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;
import net.rubygrapefruit.platform.terminal.TerminalInput;

public class WindowsTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        // Supported for Windows and Cygwin consoles
        int console = getTypeForOutput(output);
        return console != WindowsConsoleFunctions.CONSOLE_NONE;
    }

    @Override
    public boolean isTerminalInput() throws NativeException {
        // Only supported for Windows console for now
        return getTypeForInput() != WindowsConsoleFunctions.CONSOLE_NONE;
    }

    @Override
    protected TerminalInput createInput() {
        if (getTypeForInput() == WindowsConsoleFunctions.CONSOLE_CYGWIN) {
            return new PlainTerminalInput();
        }
        return new WindowsTerminalInput();
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        if (getTypeForOutput(output) == WindowsConsoleFunctions.CONSOLE_CYGWIN) {
            return new AnsiTerminal(output);
        } else {
            return new WindowsTerminal(output);
        }
    }

    private int getTypeForInput() {
        FunctionResult result = new FunctionResult();
        int console = WindowsConsoleFunctions.isConsole(NativeLibraryFunctions.STDIN, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine if stdin is a console: %s", result.getMessage()));
        }
        return console;
    }

    private int getTypeForOutput(Output output) {
        int ordinal = output == Output.Stdout ? NativeLibraryFunctions.STDOUT : NativeLibraryFunctions.STDERR;
        FunctionResult result = new FunctionResult();
        int console = WindowsConsoleFunctions.isConsole(ordinal, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine if %s is a console: %s", output,
                    result.getMessage()));
        }
        return console;
    }
}
