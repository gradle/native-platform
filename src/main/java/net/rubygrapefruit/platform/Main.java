package net.rubygrapefruit.platform;

public class Main {
    public static void main(String[] args) {
        Process process = Platform.get(Process.class);
        System.out.println("* PID: " + process.getPid());
        Terminal terminal = Platform.get(Terminal.class);
        System.out.println("* stdout: " + (terminal.isTerminal(Terminal.Output.Stdout) ? "terminal" : "not a terminal"));
        System.out.println("* stderr: " + (terminal.isTerminal(Terminal.Output.Stderr) ? "terminal" : "not a terminal"));
    }
}
