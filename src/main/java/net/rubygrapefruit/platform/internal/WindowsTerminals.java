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
import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

import java.io.PrintStream;

public class WindowsTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        FunctionResult result = new FunctionResult();
        boolean console = WindowsConsoleFunctions.isConsole(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine if %s is a console: %s", output,
                    result.getMessage()));
        }
        return console;
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        PrintStream stream = output == Terminals.Output.Stdout ? System.out : System.err;
        return new WrapperTerminal(stream, new WindowsTerminal(output));
    }
}
