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

package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf

class TerminalsTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final Terminals terminals = Native.get(Terminals.class)

    def "caches terminals instance"() {
        expect:
        Native.get(Terminals.class) == terminals
    }

    def "can check if attached to terminal"() {
        expect:
        !terminals.isTerminal(Terminals.Output.Stdout);
        !terminals.isTerminal(Terminals.Output.Stderr);
    }

    @IgnoreIf({Platform.current().windows})
    def "cannot access posix terminal from a test"() {
        when:
        terminals.getTerminal(Terminals.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open terminal for stdout: not a terminal'
    }

    @IgnoreIf({!Platform.current().windows})
    def "cannot access windows console from a test"() {
        when:
        terminals.getTerminal(Terminals.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open console for stdout: not a console'
    }
}
