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

package net.rubygrapefruit.platform.terminal

import net.rubygrapefruit.platform.Native

import java.nio.file.Files

/**
 * Subject process for tests that exercise {@link TerminalInput#rawMode()} end-to-end.
 *
 * <p>Launched as a child of a {@link PtyProcessLauncher}-managed PTY so its STDIN is a real
 * tty. Calls {@code rawMode()} on its own STDIN, prints a {@code READY} sentinel on stderr
 * to signal that raw mode is now in effect, then reads the requested number of bytes from
 * STDIN and prints each one as two lowercase hex digits on its own line to stdout.</p>
 *
 * <p>Tests drive bytes into the helper through the PTY master (the parent's
 * {@link PtyProcess#getOutputStream()}) and assert against the hex dump on stdout. This
 * pins what {@code rawMode()} actually delivers to the application's read syscalls,
 * rather than what bits the implementation sets internally.</p>
 *
 * <p>Argument: a single integer giving the number of bytes to read before the helper exits.
 * The helper does not write anything to stdout other than the hex dump; tests should
 * read stderr for the {@code READY} marker and stdout for the dump.</p>
 */
class RawInputHelper {
    static void main(String[] args) {
        int count = Integer.parseInt(args[0])
        def cacheDir = Files.createTempDirectory("native-platform-raw-input-helper").toFile()
        def nativeIntegration = Native.init(cacheDir)
        def input = nativeIntegration.get(Terminals).getTerminalInput()
        input.rawMode()
        try {
            System.err.println("READY")
            System.err.flush()
            for (int i = 0; i < count; i++) {
                int b = System.in.read()
                if (b == -1) {
                    break
                }
                System.out.printf("%02x%n", b)
                System.out.flush()
            }
        } finally {
            input.reset()
        }
    }
}
