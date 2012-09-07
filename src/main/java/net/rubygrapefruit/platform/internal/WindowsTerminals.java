package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.Terminals;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

import java.io.PrintStream;

public class WindowsTerminals extends AbstractTerminals {
    public boolean isTerminal(Output output) {
        FunctionResult result = new FunctionResult();
        boolean console = WindowsConsoleFunctions.isConsole(output.ordinal(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not determine if %s is a console: %s", output,
                    result.getMessage()));
        }
        return console;
    }

    @Override
    protected AbstractTerminal createTerminal(Output output) {
        PrintStream stream = output == Terminals.Output.Stdout ? System.out : System.err;
        return new WrapperTerminal(stream, new WindowsTerminal(output));
    }
}
