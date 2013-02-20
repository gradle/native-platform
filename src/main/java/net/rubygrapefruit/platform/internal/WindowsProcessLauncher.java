package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ProcessLauncher;
import net.rubygrapefruit.platform.internal.jni.WindowsHandleFunctions;

public class WindowsProcessLauncher implements ProcessLauncher {
    private final ProcessLauncher launcher;

    public WindowsProcessLauncher(ProcessLauncher launcher) {
        this.launcher = launcher;
    }

    public Process start(ProcessBuilder processBuilder) throws NativeException {
        FunctionResult result = new FunctionResult();
        WindowsHandleFunctions.markStandardHandlesUninheritable(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not start '%s': %s", processBuilder.command().get(0),
                    result.getMessage()));
        }
        try {
            return launcher.start(processBuilder);
        } finally {
            WindowsHandleFunctions.restoreStandardHandles(result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not restore process handles: %s", result.getMessage()));
            }
        }
    }
}
