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

import groovy.transform.PackageScope
import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform

import java.nio.file.Files

/**
 * Test fixture for {@link TerminalInput#rawMode()} byte-transparency tests: subject
 * via {@link #main}, harness via {@link #runWithInput}.
 */
class RawInputHelper {
    /**
     * Subject entry point. Launched as the child of a {@link PtyProcessLauncher}-managed
     * PTY so its STDIN is a real tty: it puts STDIN into raw mode, prints the ready
     * sentinel on stdout to signal the parent, then reads {@code args[0]} bytes from
     * STDIN and prints each one as two lowercase hex digits on its own line.
     * Single-byte expectations follow {@code od -A n -t x1}.
     */
    static void main(String[] args) {
        int count = Integer.parseInt(args[0])
        def cacheDir = Files.createTempDirectory("native-platform-raw-input-helper").toFile()
        def nativeIntegration = Native.init(cacheDir)
        def input = nativeIntegration.get(Terminals).getTerminalInput()
        input.rawMode()
        try {
            System.out.println(READY_MARKER)
            System.out.flush()
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

    @PackageScope
    static String runWithInput(PtyProcessLauncher launcher, byte[] inputBytes) {
        def pty = launcher.start(command(inputBytes.length), System.getenv(), null, 80, 24)
        def stdout = new ByteArrayOutputStream()
        def stdoutLock = new Object()
        def reader = Thread.start {
            byte[] buf = new byte[1024]
            int n
            while ((n = pty.inputStream.read(buf)) >= 0) {
                synchronized (stdoutLock) {
                    stdout.write(buf, 0, n)
                }
            }
        }
        try {
            waitForReady(stdout, stdoutLock)
            pty.outputStream.write(inputBytes)
            pty.outputStream.flush()
            def exitCode = pty.waitFor()
            reader.join(10_000)
            assert !reader.isAlive()
            assert exitCode == 0: "helper exited with ${exitCode}; captured: ${stdout}"
            return extractHex(stdout.toString(), inputBytes.length)
        } finally {
            pty?.close()
        }
    }

    private static final String READY_MARKER = "RAW_INPUT_HELPER_READY"

    private static List<String> command(int byteCount) {
        def javaHome = System.getProperty("java.home")
        def suffix = Platform.current().windows ? ".exe" : ""
        def javaExe = "${javaHome}/bin/java${suffix}".toString()
        def classpath = System.getProperty("java.class.path")
        return [javaExe, "-cp", classpath, RawInputHelper.name, String.valueOf(byteCount)]
    }

    private static void waitForReady(ByteArrayOutputStream stdout, Object stdoutLock) {
        long deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            String captured
            synchronized (stdoutLock) {
                captured = stdout.toString()
            }
            if (captured.contains(READY_MARKER)) {
                return
            }
            Thread.sleep(50)
        }
        synchronized (stdoutLock) {
            throw new AssertionError("timed out waiting for ${READY_MARKER}; captured: ${stdout}")
        }
    }

    private static String extractHex(String captured, int byteCount) {
        def hexLine = ~/^[0-9a-f]{2}$/
        def hexLines = captured.split('\\R')
            .collect { it.trim() }
            .findAll { it ==~ hexLine }
        assert hexLines.size() == byteCount: "expected ${byteCount} hex lines, captured: ${captured}"
        return hexLines.join(" ")
    }
}
