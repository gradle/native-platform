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

        // 2. Construct the process before forking. The constructor starts
        //    master-side drainer threads on masterFd and stderrReadFd, so
        //    a read is on the way to being parked before the child can
        //    write its first byte. Combined with the deferred parent-slave
        //    close (the waiter holds slaveFd + stderrWriteFd open until
        //    waitpid returns), this closes the POSIX "master read after
        //    slave close is implementation-defined" race on FreeBSD.
        PosixPtyProcess process = new PosixPtyProcess(fds[0], fds[1], fds[2], fds[3]);

        // 3. Yield briefly so the drainer threads have a chance to be
        //    scheduled and reach their first nativeRead syscall before the
        //    child can run. Empirically, 4 concurrent launcher.start calls
        //    on a loaded FreeBSD agent need this nudge — Thread.start
        //    alone leaves a window where a drainer is runnable but not yet
        //    parked when its child writes-and-exits.
        process.awaitDrainersScheduled();

        // 4. Fork+exec. On success the parent keeps slave + stderrWrite
        //    open and the process owns them; on failure the JNI side
        //    closes both.
        FunctionResult spawnResult = new FunctionResult();
        long pid = PosixPtyFunctions.spawnInPty(
                fds[1], fds[3], cmd, env, dir, spawnResult);
        if (spawnResult.isFailed()) {
            // slave + stderrWrite already closed by the JNI failure path;
            // tell the process not to close them again.
            process.markSlaveAlreadyClosed();
            process.close();
            throw new NativeException("Could not spawn PTY process: " + spawnResult.getMessage());
        }

        // 4. Attach pid. Starts the waiter thread, which calls waitpid
        //    and then closes the parent's slave + stderrWrite fds.
        process.attachPid(pid);
        return process;
    }
}
