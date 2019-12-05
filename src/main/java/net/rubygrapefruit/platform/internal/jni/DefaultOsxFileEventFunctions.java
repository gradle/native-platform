package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.Collection;

public class DefaultOsxFileEventFunctions implements NativeIntegration {

    public void startWatch(Collection<String> paths, double latency, OsxFileEventFunctions.ChangeCallback callback) {
        FunctionResult result = new FunctionResult();
        if (!paths.isEmpty()) {
            OsxFileEventFunctions.startWatch(paths.toArray(new String[0]), latency, callback, result);
        }
        if (result.isFailed()) {
            throw new NativeException("Failed to start collecting changes. Reason: " + result.getMessage());
        }
    }

    public void stopWatch() {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.stopWatch(result);
        if (result.isFailed()) {
            throw new NativeException("Failed to get changed files. Reason: " + result.getMessage());
        }
    }
}
