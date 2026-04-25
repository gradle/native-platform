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
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;
import net.rubygrapefruit.platform.terminal.PtyProcessLauncher;

import java.io.File;
import java.util.List;
import java.util.Map;

public class WindowsPtyProcessLauncher implements PtyProcessLauncher {

    @Override
    public boolean isAvailable() {
        try {
            return WindowsPtyFunctions.isConPtyAvailable();
        } catch (Throwable ignored) {
            return false;
        }
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

        long[] ptyHandles = new long[4];
        FunctionResult ptyResult = new FunctionResult();
        WindowsPtyFunctions.createPseudoConsole(cols, rows, ptyHandles, ptyResult);
        if (ptyResult.isFailed()) {
            throw new NativeException("Could not create pseudo-console: " + ptyResult.getMessage());
        }
        long hPC = ptyHandles[0];
        long ptyReadHandle = ptyHandles[1];
        long ptyWriteHandle = ptyHandles[2];

        // Construct the process before the child exists so its stdout drainer
        // can attach to ptyReadHandle and start consuming ConPTY's startup VT
        // output before CreateProcessW fires; otherwise cmd.exe's first write
        // would block on a full pipe. The stderr handle is filled in via
        // attachProcess once spawnConPtyProcess returns: on Windows the stderr
        // pipe is created inside the spawn so its inheritable write end can be
        // included in the STARTUPINFOEX handle whitelist.
        WindowsPtyProcess process = new WindowsPtyProcess(hPC, ptyReadHandle, ptyWriteHandle,
                /*stderrReadHandle=*/0L, /*processHandle=*/0L, /*pid=*/0L, /*stderrMerged=*/true);

        long pid;
        try {
            // Slot 0: process handle. Slot 1: stderr read handle, or 0 if
            // CreateProcessW rejected the split-stderr handle list and the
            // implementation fell back to merged stderr.
            long[] procHandles = new long[2];
            FunctionResult procResult = new FunctionResult();
            pid = WindowsPtyFunctions.spawnConPtyProcess(hPC, cmd, env, dir, procHandles, procResult);
            if (procResult.isFailed()) {
                throw new NativeException("Could not spawn ConPTY process: " + procResult.getMessage());
            }
            process.attachProcess(procHandles[0], pid, procHandles[1]);
        } catch (Throwable t) {
            process.close();
            throw t;
        }
        return process;
    }
}
