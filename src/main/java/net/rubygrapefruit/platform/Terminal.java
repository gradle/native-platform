package net.rubygrapefruit.platform;

public interface Terminal extends NativeIntegration {
    enum Output {Stdout, Stderr}

    boolean isTerminal(Output output);

    TerminalSize getTerminalSize(Output output);
}
