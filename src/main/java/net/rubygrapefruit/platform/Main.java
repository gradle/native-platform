package net.rubygrapefruit.platform;

import java.awt.*;

public class Main {
    public static void main(String[] args) {
        Process process = Platform.get(Process.class);
        System.out.println("* PID: " + process.getPid());

        Terminal terminal = Platform.get(Terminal.class);

        boolean stdoutIsTerminal = terminal.isTerminal(Terminal.Output.Stdout);
        boolean stderrIsTerminal = terminal.isTerminal(Terminal.Output.Stderr);
        System.out.println("* stdout: " + (stdoutIsTerminal ? "terminal" : "not a terminal"));
        System.out.println("* stderr: " + (stderrIsTerminal ? "terminal" : "not a terminal"));
        if (stdoutIsTerminal) {
            TerminalSize terminalSize = terminal.getTerminalSize(Terminal.Output.Stdout);
            System.out.println("* terminal size: " + terminalSize.getCols() + " cols x " + terminalSize.getRows() + " rows");
        }
    }
}
