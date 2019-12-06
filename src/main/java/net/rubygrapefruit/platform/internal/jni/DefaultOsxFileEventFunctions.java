package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.Collection;

public class DefaultOsxFileEventFunctions implements NativeIntegration {

    public FileWatcher startWatch(Collection<String> paths, double latency, OsxFileEventFunctions.ChangeCallback callback) {
        if (paths.isEmpty()) {
            return FileWatcher.EMPTY;
        }
        FunctionResult result = new FunctionResult();
        FileWatcher watch = OsxFileEventFunctions.startWatch(paths.toArray(new String[0]), latency, callback, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watch. Reason: " + result.getMessage());
        }
        return watch;
    }

    public static class WatchImpl implements FileWatcher {
        private Object details;

        public WatchImpl(Object details) {
            this.details = details;
        }

        @Override
        public void close() {
            if (details == null) {
                return;
            }
            FunctionResult result = new FunctionResult();
            OsxFileEventFunctions.stopWatch(details, result);
            details = null;
            if (result.isFailed()) {
                throw new NativeException("Failed to stop watch. Reason: " + result.getMessage());
            }
        }
    }
}
