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
}
