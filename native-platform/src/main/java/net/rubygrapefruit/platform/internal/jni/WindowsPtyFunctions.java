/*
 * Copyright 2026 the original author or authors.
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

public class WindowsPtyFunctions {

    public static native boolean isConPtyAvailable();

    public static native long spawnConPty(String[] command,
                                          String[] environment,
                                          String workingDir,
                                          int cols, int rows,
                                          long[] outHandles,
                                          FunctionResult result);

    public static native void resizePseudoConsole(long hPC, int cols, int rows, FunctionResult result);

    public static native void closePseudoConsole(long hPC, FunctionResult result);

    public static native int waitForProcess(long processHandle, FunctionResult result);

    public static native boolean hasProcessExited(long processHandle, int[] exitCode, FunctionResult result);

    public static native void destroyProcess(long processHandle, long ptyWriteHandle, int gracePeriodMs, FunctionResult result);

    public static native void cancelSynchronousIo(long threadHandle, FunctionResult result);

    public static native void closeHandle(long handle, FunctionResult result);

    public static native int nativeRead(long handle, byte[] buf, int off, int len, FunctionResult result);

    public static native int nativeWrite(long handle, byte[] buf, int off, int len, FunctionResult result);
}
