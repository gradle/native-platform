package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf

class TerminalTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final Terminals terminal = Native.get(Terminals.class)

    def "can check if attached to terminal"() {
        expect:
        !terminal.isTerminal(Terminals.Output.Stdout);
        !terminal.isTerminal(Terminals.Output.Stderr);
    }

    @IgnoreIf({Platform.current().windows})
    def "cannot access posix terminal from a test"() {
        when:
        terminal.getTerminal(Terminals.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open terminal for stdout: not a terminal'
    }

    @IgnoreIf({!Platform.current().windows})
    def "cannot access windows console from a test"() {
        when:
        terminal.getTerminal(Terminals.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message == 'Could not open console for stdout: not a console'
    }
}
