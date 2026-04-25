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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.PosixPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;
import net.rubygrapefruit.platform.terminal.PtyProcessLauncher;

import java.io.File;
import java.util.List;
import java.util.Map;

public class PosixPtyProcessLauncher implements PtyProcessLauncher {

    @Override
    public boolean isAvailable() {
        return PosixPtyFunctions.isPtyAvailable();
    }

    @Override
    public PtyProcess start(List<String> command, Map<String, String> environment,
                            File workingDir, int cols, int rows) {
        String[] cmd = command.toArray(new String[0]);
        String[] env = new String[environment.size()];
        int i = 0;
        for (Map.Entry<String, String> e : environment.entrySet()) {
            env[i++] = e.getKey() + "=" + e.getValue();
        }
        String dir = workingDir != null ? workingDir.getAbsolutePath() : null;

        // 1. Allocate the PTY + stderr pipe. No fork yet.
        int[] fds = new int[]{-1, -1, -1, -1}; // master, slave, stderrRead, stderrWrite
        FunctionResult ptyResult = new FunctionResult();
        PosixPtyFunctions.createPty(cols, rows, fds, ptyResult);
        if (ptyResult.isFailed()) {
            throw new NativeException("Could not create PTY: " + ptyResult.getMessage());
        }

        // 2. Build the process with pid=0. Its constructor starts master-side
        //    drainer threads on masterFd and stderrReadFd, so a read is parked
        //    on the master before the child runs. Required for portability —
        //    POSIX leaves master-read after slave close implementation-defined
        //    and FreeBSD's pty driver discards it.
        PosixPtyProcess process = new PosixPtyProcess(fds[0], fds[2], 0L);

        // 3. Fork+exec into the existing PTY. spawnInPty closes slaveFd and
        //    stderrWriteFd in the parent regardless of outcome.
        try {
            FunctionResult spawnResult = new FunctionResult();
            long pid = PosixPtyFunctions.spawnInPty(
                    fds[1], fds[3], cmd, env, dir, spawnResult);
            if (spawnResult.isFailed()) {
                throw new NativeException("Could not spawn PTY process: " + spawnResult.getMessage());
            }
            process.attachPid(pid);
        } catch (Throwable t) {
            process.close();
            throw t;
        }
        return process;
    }
}
