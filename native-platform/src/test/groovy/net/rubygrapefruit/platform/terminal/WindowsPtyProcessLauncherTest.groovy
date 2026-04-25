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
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions
import spock.lang.IgnoreIf
import spock.lang.Timeout

@Timeout(30)
@IgnoreIf({ !Platform.current().windows })
class WindowsPtyProcessLauncherTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    static boolean conptyAvailable() {
        try {
            getIntegration(PtyProcessLauncher)
            return WindowsPtyFunctions.isConPtyAvailable()
        } catch (Throwable ignored) {
            return false
        }
    }

    def "isConPtyAvailable returns a boolean and does not throw"() {
        when:
        boolean available = WindowsPtyFunctions.isConPtyAvailable()
        boolean launcherAvailable = launcher.isAvailable()
        println "[Tier 6.0.1] WindowsPtyFunctions.isConPtyAvailable=${available} PtyProcessLauncher.isAvailable=${launcherAvailable}"

        then:
        noExceptionThrown()
    }

    def "isConPtyAvailable matches launcher.isAvailable"() {
        expect:
        WindowsPtyFunctions.isConPtyAvailable() == launcher.isAvailable()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "can spawn a basic ConPTY process and observe exit 0"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }
}
