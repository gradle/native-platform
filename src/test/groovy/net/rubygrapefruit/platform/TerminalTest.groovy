package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf

class TerminalTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final TerminalAccess terminal = Native.get(TerminalAccess.class)

    def "can check if attached to terminal"() {
        expect:
        !terminal.isTerminal(TerminalAccess.Output.Stdout);
        !terminal.isTerminal(TerminalAccess.Output.Stderr);
    }

    @IgnoreIf({Platform.current().windows})
    def "cannot access posix terminal from a test"() {
        when:
        terminal.getTerminal(TerminalAccess.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open terminal for stdout: not a terminal'
    }

    @IgnoreIf({!Platform.current().windows})
    def "cannot access windows console from a test"() {
        when:
        terminal.getTerminal(TerminalAccess.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open console for stdout: not a console'
    }
}
