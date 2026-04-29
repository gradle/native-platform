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

import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * Tier 8.1 — POSIX raw-mode byte transparency.
 *
 * <p>Pins what {@code TerminalInput.rawMode()} actually delivers to the application's
 * read syscalls when the calling process's STDIN is a real PTY slave. Each test
 * launches {@code RawInputHelper} (from {@code test-app}) through the
 * {@code PtyProcessLauncher}, drives bytes into the helper through the PTY master, and
 * asserts the helper's hex dump matches what was sent — i.e., the raw-mode termios
 * configuration leaves the byte stream untouched.</p>
 */
@Timeout(30)
@IgnoreIf({ Platform.current().windows })
class PosixRawInputModeTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    private static List<String> helperCommand(int byteCount) {
        def javaHome = System.getProperty("java.home")
        def javaExe = "${javaHome}/bin/java".toString()
        def classpath = System.getProperty("java.class.path")
        return [javaExe, "-cp", classpath, RawInputHelper.name, String.valueOf(byteCount)]
    }

    private String runHelperWithInput(byte[] inputBytes) {
        def pty = launcher.start(helperCommand(inputBytes.length), System.getenv(), null, 80, 24)
        def stdout = new ByteArrayOutputStream()
        def reader = Thread.start {
            byte[] buf = new byte[1024]
            int n
            while ((n = pty.inputStream.read(buf)) >= 0) {
                stdout.write(buf, 0, n)
            }
        }
        def stderrReader = new BufferedReader(new InputStreamReader(pty.errorStream))
        try {
            def ready = stderrReader.readLine()
            assert ready == "READY"
            pty.outputStream.write(inputBytes)
            pty.outputStream.flush()
            def exitCode = pty.waitFor()
            reader.join(5_000)
            assert !reader.isAlive()
            assert exitCode == 0
            return stdout.toString().replaceAll('\\s+', ' ').trim()
        } finally {
            pty?.close()
        }
    }

    def "Enter delivered as 0x0D"() {
        when:
        def hex = runHelperWithInput([0x0D] as byte[])

        then:
        hex == "0d"
    }

    def "Ctrl+C delivered as 0x03"() {
        when:
        def hex = runHelperWithInput([0x03] as byte[])

        then:
        hex == "03"
    }

    def "XOFF delivered as 0x11"() {
        when:
        def hex = runHelperWithInput([0x11] as byte[])

        then:
        hex == "11"
    }

    def "mixed-byte burst including UTF-8 high-bit survives intact"() {
        when:
        // \r and \x11 (XOFF) are the bytes the partial-clear rawMode swallows.
        // \xE2\x9C\x93 is UTF-8 ✓ — exercises 8-bit-clean delivery (CS8).
        def hex = runHelperWithInput([0x0D, 0x11, 0xE2 as byte, 0x9C as byte, 0x93 as byte] as byte[])

        then:
        hex == "0d 11 e2 9c 93"
    }
}
