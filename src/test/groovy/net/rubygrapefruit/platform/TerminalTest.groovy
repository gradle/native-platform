package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TerminalTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final Terminal terminal = Platform.get(Terminal.class)

    def "can check if attached to terminal"() {
        expect:
        !terminal.isTerminal(Terminal.Output.Stdout);
        !terminal.isTerminal(Terminal.Output.Stderr);
    }

    def "cannot determine terminal size from a test"() {
        when:
        terminal.getTerminalSize(Terminal.Output.Stdout)

        then:
        NativeException e = thrown()
        e.message.startsWith('Could not get terminal size. Errno is ')
    }
}
