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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * Tier 8.2 — Windows raw-mode byte transparency.
 *
 * <p>Pins what {@code TerminalInput.rawMode()} actually delivers to the application's
 * read syscalls when the calling process's STDIN is a real ConPTY-attached console.
 * Each test launches {@link RawInputHelper} through the {@link PtyProcessLauncher},
 * drives bytes into the helper through the ConPTY input handle, and asserts the
 * helper's hex dump matches what was sent — i.e., the raw-mode console-mode
 * configuration leaves the byte stream untouched.</p>
 */
@Timeout(30)
@IgnoreIf({ !Platform.current().windows })
class WindowsRawInputModeTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    static boolean conptyAvailable() {
        try {
            getIntegration(PtyProcessLauncher)
            return WindowsPtyFunctions.isConPtyAvailable()
        } catch (Throwable ignored) {
            return false
        }
    }

    @IgnoreIf({ !WindowsRawInputModeTest.conptyAvailable() })
    def "Enter delivered as 0x0D"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x0D] as byte[])

        then:
        hex == "0d"
    }

    @IgnoreIf({ !WindowsRawInputModeTest.conptyAvailable() })
    def "Ctrl+C delivered as 0x03 without killing the helper"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x03] as byte[])

        then:
        hex == "03"
    }

    @IgnoreIf({ !WindowsRawInputModeTest.conptyAvailable() })
    def "up-arrow VT escape delivered as 0x1B 0x5B 0x41"() {
        when:
        def hex = RawInputHelper.runWithInput(launcher, [0x1B, 0x5B, 0x41] as byte[])

        then:
        hex == "1b 5b 41"
    }

    def "rawInputMode against a non-console stdin reports the failure as a NativeException"() {
        given:
        // The test JVM's stdin is not a console, so the JNI's GetConsoleMode (or the
        // SetConsoleMode that follows) fails and FunctionResult is marked failed.
        // WindowsTerminalInput.rawMode() must surface that as a NativeException.
        def terminals = getIntegration(net.rubygrapefruit.platform.terminal.Terminals)
        def input = terminals.getTerminalInput()

        when:
        input.rawMode()

        then:
        thrown(NativeException)
    }
}
