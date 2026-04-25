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

        FunctionResult result = new FunctionResult();
        long[] outHandles = new long[5];
        long pid = WindowsPtyFunctions.spawnConPty(cmd, env, dir, cols, rows, outHandles, result);
        if (result.isFailed()) {
            throw new NativeException("Could not spawn ConPTY process: " + result.getMessage());
        }
        boolean stderrMerged = (outHandles[3] == 0);
        return new WindowsPtyProcess(
                outHandles[0], outHandles[1], outHandles[2],
                outHandles[3], outHandles[4], pid, stderrMerged);
    }
}
