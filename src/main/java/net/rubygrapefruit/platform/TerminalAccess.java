package net.rubygrapefruit.platform;

public interface TerminalAccess extends NativeIntegration {
    enum Output {Stdout, Stderr}

    boolean isTerminal(Output output);

    Terminal getTerminal(Output output);
}
