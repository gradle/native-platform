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

        // 1. Allocate the PTY + stderr pipe.  No fork yet.
        int[] fds = new int[]{-1, -1, -1, -1}; // master, slave, stderrRead, stderrWrite
        FunctionResult ptyResult = new FunctionResult();
        PosixPtyFunctions.createPty(cols, rows, fds, ptyResult);
        if (ptyResult.isFailed()) {
            throw new NativeException("Could not create PTY: " + ptyResult.getMessage());
        }

        // 2. Construct the process before forking.  The constructor starts
        //    master-side drainer threads on masterFd and stderrReadFd so
        //    they are scheduling onto their first read before the
        //    grandchild can write its first byte.
        PosixPtyProcess process = new PosixPtyProcess(fds[0], fds[2]);

        // 3. Two-fork spawn.  spawnInPty closes slave + stderrWrite in the
        //    daemon regardless of outcome.  On success it returns the
        //    grandchild pid and populates outAux with the anchor pid plus
        //    the info-pipe-read and sync-pipe-write fds the waiter thread
        //    uses to receive the exit status and release the anchor.
        long[] outAux = new long[3];
        FunctionResult spawnResult = new FunctionResult();
        long pid = PosixPtyFunctions.spawnInPty(
                fds[1], fds[3], cmd, env, dir, outAux, spawnResult);
        if (spawnResult.isFailed()) {
            // Slave + stderrWrite are already closed by the JNI failure
            // path.  Master + stderrRead are still open and have drainer
            // threads parked on them; closeAfterSpawnFailure releases them.
            process.closeAfterSpawnFailure();
            throw new NativeException("Could not spawn PTY process: " + spawnResult.getMessage());
        }

        // 4. Hand the anchor handles to the process and start the waiter.
        process.attachAnchor(pid, outAux[0], (int) outAux[1], (int) outAux[2]);
        return process;
    }
}
