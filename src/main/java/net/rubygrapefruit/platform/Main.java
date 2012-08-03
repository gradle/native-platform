package net.rubygrapefruit.platform;

public class Main {
    public static void main(String[] args) {
        Process process = Platform.get(Process.class);
        System.out.println("* PID: " + process.getPid());

        TerminalAccess terminalAccess = Platform.get(TerminalAccess.class);

        boolean stdoutIsTerminal = terminalAccess.isTerminal(TerminalAccess.Output.Stdout);
        boolean stderrIsTerminal = terminalAccess.isTerminal(TerminalAccess.Output.Stderr);
        System.out.println("* stdout: " + (stdoutIsTerminal ? "terminal" : "not a terminal"));
        System.out.println("* stderr: " + (stderrIsTerminal ? "terminal" : "not a terminal"));
        if (stdoutIsTerminal) {
            Terminal terminal = terminalAccess.getTerminal(TerminalAccess.Output.Stdout);
            TerminalSize terminalSize = terminal.getTerminalSize();
            System.out.println("* terminal size: " + terminalSize.getCols() + " cols x " + terminalSize.getRows() + " rows");
            System.out.print("[normal] ");
            terminal.bold();
            System.out.print("[bold]");
            terminal.normal();
            System.out.println(" [normal]");
        }
    }
}
