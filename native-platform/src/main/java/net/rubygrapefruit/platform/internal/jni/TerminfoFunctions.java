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
    public static native String getVersion();

    /**
     * Sets up output.
     */
    public static native void initTerminal(int filedes, TerminalCapabilities terminalCapabilities, FunctionResult result);

    public static native byte[] boldOn(FunctionResult result);

    public static native byte[] dimOn(FunctionResult result);

    // May be null
    public static native byte[] reset(FunctionResult result);

    public static native byte[] foreground(int ansiColor, FunctionResult result);

    public static native byte[] defaultForeground(FunctionResult result);

    public static native byte[] hideCursor(FunctionResult result);

    public static native byte[] showCursor(FunctionResult result);

    public static native byte[] left(FunctionResult result);

    public static native byte[] right(FunctionResult result);

    public static native byte[] up(FunctionResult result);

    public static native byte[] down(FunctionResult result);

    public static native byte[] startLine(FunctionResult result);

    public static native byte[] clearToEndOfLine(FunctionResult result);
}
