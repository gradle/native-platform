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

package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;
import net.rubygrapefruit.platform.internal.TerminalCapabilities;

public class TerminfoFunctions {
    public static native int getVersion();

    /**
     * Sets up terminal info and switches output to normal mode.
     */
    public static native void initTerminal(int filedes, TerminalCapabilities terminalCapabilities, FunctionResult result);

    public static native void bold(FunctionResult result);

    public static native void reset(FunctionResult result);

    public static native void foreground(int ansiColor, FunctionResult result);

    public static native void left(int count, FunctionResult result);

    public static native void right(int count, FunctionResult result);

    public static native void up(int count, FunctionResult result);

    public static native void down(int count, FunctionResult result);

    public static native void startLine(FunctionResult result);

    public static native void clearToEndOfLine(FunctionResult result);
}
