package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TerminalTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final TerminalAccess terminal = Native.get(TerminalAccess.class)

    def "can check if attached to terminal"() {
        expect:
        !terminal.isTerminal(TerminalAccess.Output.Stdout);
        !terminal.isTerminal(TerminalAccess.Output.Stderr);
    }

    def "cannot access terminal from a test"() {
        when:
        terminal.getTerminal(TerminalAccess.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open terminal: not a terminal'
    }
}
