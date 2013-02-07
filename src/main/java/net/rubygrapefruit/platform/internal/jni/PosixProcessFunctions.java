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

public class PosixProcessFunctions {
    public static native int getPid();

    public static native String getWorkingDirectory(FunctionResult result);

    public static native void setWorkingDirectory(String dir, FunctionResult result);

    public static native String getEnvironmentVariable(String var, FunctionResult result);

    public static native void setEnvironmentVariable(String var, String value, FunctionResult result);
}
