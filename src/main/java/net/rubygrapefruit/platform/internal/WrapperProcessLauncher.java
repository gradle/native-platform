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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ProcessLauncher;
import net.rubygrapefruit.platform.ThreadSafe;

@ThreadSafe
public class WrapperProcessLauncher implements ProcessLauncher {
    private final Object startLock = new Object();
    private final ProcessLauncher launcher;

    public WrapperProcessLauncher(ProcessLauncher launcher) {
        this.launcher = launcher;
    }

    public Process start(ProcessBuilder processBuilder) throws NativeException {
        synchronized (startLock) {
            // Start a single process at a time, to avoid streams to child process being inherited by other
            // children before the parent can close them
            return launcher.start(processBuilder);
        }
    }
}
