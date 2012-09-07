package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.WindowsConsoleFunctions;

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
        return new WindowsTerminal(output);
    }
}
