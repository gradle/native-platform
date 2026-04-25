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

public class PosixPtyFunctions {

    public static final int EIO = 5;
    public static final int ENXIO = 6;
    public static final int SIGTERM = 15;
    public static final int SIGKILL = 9;

    public static native boolean isPtyAvailable();

    /**
     * Allocates the PTY pair plus the stderr pipe and applies the initial
     * terminal size. Does not fork. Returns four fds in {@code outFds}:
     * {@code [masterFd, slaveFd, stderrReadFd, stderrWriteFd]}. The caller
     * is expected to start drainer threads on {@code masterFd} and
     * {@code stderrReadFd} before invoking {@link #spawnInPty}, then hand
     * {@code slaveFd} and {@code stderrWriteFd} to that call.
     */
    public static native void createPty(int cols, int rows,
                                        int[] outFds,
                                        FunctionResult result);

    /**
     * Forks and execs the child inside an existing PTY allocated by
     * {@link #createPty}. Closes {@code slaveFd} and {@code stderrWriteFd}
     * in the parent regardless of outcome — the child inherits its own
     * copies via fork.
     */
    public static native long spawnInPty(int slaveFd, int stderrWriteFd,
                                         String[] command,
                                         String[] environment,
                                         String workingDir,
                                         FunctionResult result);

    public static native int waitPid(long pid, FunctionResult result);

    public static native void closeFd(int fd, FunctionResult result);

    public static native void setPtySize(int masterFd, int cols, int rows, FunctionResult result);

    public static native void killProcess(long pid, int signal, FunctionResult result);

    public static native int nativeRead(int fd, byte[] buf, int off, int len, FunctionResult result);

    public static native int nativeWrite(int fd, byte[] buf, int off, int len, FunctionResult result);
}
