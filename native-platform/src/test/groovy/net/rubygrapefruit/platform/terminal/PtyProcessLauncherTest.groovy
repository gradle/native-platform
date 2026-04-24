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

@Timeout(30)
@IgnoreIf({ Platform.current().windows })
class PtyProcessLauncherTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    def "isAvailable does not throw and returns a boolean"() {
        when:
        def result = launcher.isAvailable()

        then:
        noExceptionThrown()
        result instanceof Boolean
    }

    def "can start /usr/bin/true and observe exit code 0"() {
        given:
        def pty = launcher.start(["/usr/bin/true"], System.getenv(), null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }
}
