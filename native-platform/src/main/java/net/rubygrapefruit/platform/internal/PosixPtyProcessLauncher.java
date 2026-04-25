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

        // 2. Fork+exec. On success the parent keeps slave + stderrWrite open;
        //    PosixPtyProcess holds them and its waiter thread closes them
        //    after waitpid returns. Keeping them open across the child's own
        //    exit is what closes the POSIX "master read after slave close is
        //    implementation-defined" race — the slave's refcount stays at
        //    1 (parent) until we drop it deliberately, so FreeBSD's pty
        //    driver cannot flush the line discipline buffer before the
        //    parent has read it. On failure the JNI side closes both.
        FunctionResult spawnResult = new FunctionResult();
        long pid = PosixPtyFunctions.spawnInPty(
                fds[1], fds[3], cmd, env, dir, spawnResult);
        if (spawnResult.isFailed()) {
            // slave + stderrWrite already closed by the JNI failure path.
            // Close the master + stderrRead too.
            PosixPtyFunctions.closeFd(fds[0], new FunctionResult());
            PosixPtyFunctions.closeFd(fds[2], new FunctionResult());
            throw new NativeException("Could not spawn PTY process: " + spawnResult.getMessage());
        }

        // 3. Hand off all four fds to the process. attachPid starts the
        //    waiter thread, which is responsible for the deferred slave
        //    close once the child has been reaped.
        PosixPtyProcess process = new PosixPtyProcess(fds[0], fds[1], fds[2], fds[3]);
        process.attachPid(pid);
        return process;
    }
}
