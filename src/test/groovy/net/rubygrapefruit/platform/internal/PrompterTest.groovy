package net.rubygrapefruit.platform.internal

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.prompts.Prompter
import net.rubygrapefruit.platform.terminal.TerminalInput
import net.rubygrapefruit.platform.terminal.TerminalInputListener
import net.rubygrapefruit.platform.terminal.Terminals
import spock.lang.Specification
import spock.lang.Unroll


class PrompterTest extends Specification {
    def terminals = Stub(Terminals)

    @Unroll
    def "returns default selection when blank line typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.select("thing", ["a", "b"], 1)

        then:
        selection == 1

        where:
        text << ["\n", "  \t\n", "thing\n\n", "thing\nthing\n\n"]
    }

    @Unroll
    def "returns selection when number typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.select("thing", ["a", "b", "c"], 2)

        then:
        selection == 1 // base zero

        where:
        text << ["2\n", " 2 \n", "thing\n2\n", "0\n2\n", "4\n2\n"]
    }

    @Unroll
    def "returns null selection when end-of-input reached"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.select("thing", ["a", "b"], 1)

        then:
        selection == null

        where:
        text << ["", "  \t", "1", "thing\n", "3\n"]
    }

    @Unroll
    def "returns default text when blank line typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def result = prompter.enterText("thing", "a")

        then:
        result == "a"

        where:
        text << ["\n", "  \t\n"]
    }

    @Unroll
    def "returns text typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def result = prompter.enterText("thing", "a")

        then:
        result == "thing"

        where:
        text << ["thing\n", " thing \n"]
    }

    @Unroll
    def "returns null text when end-of-input reached"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def result = prompter.enterText("thing", "a")

        then:
        result == null

        where:
        text << ["", "  \t", "thing"]
    }

    @Unroll
    def "returns default boolean when blank line typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.askYesNo("thing", true)

        then:
        selection

        where:
        text << ["\n", "  \t\n", "thing\n\n"]
    }

    @Unroll
    def "returns true when yes typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.askYesNo("thing", false)

        then:
        selection

        where:
        text << ["y\n", "Y\n", "yes\n", "YES\n", "thing\ny\n"]
    }

    @Unroll
    def "returns false when no typed"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def selection = prompter.askYesNo("thing", true)

        then:
        !selection

        where:
        text << ["n\n", "N\n", "no\n", "NO\n", "thing\nn\n"]
    }

    @Unroll
    def "returns null boolean when end-of-input reached"() {
        isInteractive()
        input(text)

        def prompter = new Prompter(terminals)

        when:
        def result = prompter.askYesNo("thing", true)

        then:
        result == null

        where:
        text << ["", "  \t", "thing", "thing\nthing"]
    }

    def input(String content) {
        _ * terminals.terminalInput >> new TestInput(content)
    }

    def isInteractive() {
        _ * terminals.isTerminal(Terminals.Output.Stdout) >> true
        _ * terminals.isTerminalInput() >> true
    }

    static class TestInput implements TerminalInput {
        final InputStream inputStream

        TestInput(String content) {
            inputStream = new ByteArrayInputStream(content.bytes)
        }

        @Override
        TerminalInput rawMode() throws NativeException {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean supportsRawMode() {
            return false
        }

        @Override
        TerminalInput reset() throws NativeException {
            return this
        }

        @Override
        void read(TerminalInputListener listener) throws NativeException {
            int ch = inputStream.read()
            if (ch < 0) {
                listener.endInput()
            } else if (ch == '\n' as char) {
                listener.controlKey(TerminalInputListener.Key.Enter)
            } else {
                listener.character(ch as char)
            }
        }
    }
}
