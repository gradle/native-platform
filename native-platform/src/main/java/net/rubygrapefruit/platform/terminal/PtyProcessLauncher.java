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

package net.rubygrapefruit.platform.terminal;

import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Launches a child process with stdin+stdout connected to a new PTY
 * and stderr connected to a separate pipe.
 *
 * <p>PTY support depends on OS-level APIs that may not be available on all
 * systems.  Callers should check {@link #isAvailable()} before calling
 * {@link #start} if they intend to fall back gracefully.</p>
 */
@ThreadSafe
public interface PtyProcessLauncher extends NativeIntegration {

    /**
     * Returns {@code true} if the current operating system supports
     * PTY allocation.
     *
     * <p>On POSIX systems this verifies that {@code openpty(3)} works
     * (attempts a test allocation and immediately closes both fds).
     * On Windows this checks for the {@code CreatePseudoConsole} API
     * (Windows 10 version 1809 / build 17763 or later).</p>
     *
     * <p>This method never throws.  If it returns {@code false}, calling
     * {@link #start} will fail with a {@link net.rubygrapefruit.platform.NativeException}.</p>
     */
    boolean isAvailable();

    /**
     * Starts a new process.
     *
     * @param command     The command and its arguments.
     * @param environment The environment variables for the child (complete, not merged).
     * @param workingDir  The working directory, or null for the current directory.
     * @param cols        Initial terminal width.
     * @param rows        Initial terminal height.
     * @return A handle to the running child with its PTY master streams.
     * @throws net.rubygrapefruit.platform.NativeException if PTY allocation
     *         fails (e.g., OS does not support the required API).
     */
    @ThreadSafe
    PtyProcess start(List<String> command, Map<String, String> environment,
                     File workingDir, int cols, int rows);
}
