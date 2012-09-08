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
