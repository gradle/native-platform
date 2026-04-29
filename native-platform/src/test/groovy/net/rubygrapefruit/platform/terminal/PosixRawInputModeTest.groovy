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
 * launches {@link RawInputHelper} through the {@link PtyProcessLauncher}, drives bytes
 * into the helper through the PTY master, and asserts the helper's hex dump matches
 * what was sent — i.e., the raw-mode termios configuration leaves the byte stream
 * untouched.</p>
 */
@Timeout(30)
@IgnoreIf({ Platform.current().windows })
class PosixRawInputModeTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    def "Enter delivered as 0x0D"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x0D] as byte[])

        then:
        hex == "0d"
    }

    def "Ctrl+C delivered as 0x03"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x03] as byte[])

        then:
        hex == "03"
    }

    def "XOFF delivered as 0x11"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x11] as byte[])

        then:
        hex == "11"
    }

    def "mixed-byte burst including UTF-8 high-bit survives intact"() {
        when:
        // \r and \x11 (XOFF) are the bytes the partial-clear rawMode swallows.
        // \xE2\x9C\x93 is UTF-8 ✓ — exercises 8-bit-clean delivery (CS8).
        def hex = RawInputHelper.runWithInput(launcher, [0x0D, 0x11, 0xE2 as byte, 0x9C as byte, 0x93 as byte] as byte[])

        then:
        hex == "0d 11 e2 9c 93"
    }
}
