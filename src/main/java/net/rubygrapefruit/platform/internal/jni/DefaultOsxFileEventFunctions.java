package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultOsxFileEventFunctions implements NativeIntegration {

    public boolean addRecursiveWatch(String path) {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.createWatch(path, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watching " + path);
        }
        return true;
    }

    interface ChangeCallback {
        @SuppressWarnings("unused")
        // invoked from native code
        void pathChanged(String path);
    }

    public static class ChangeCollector implements ChangeCallback {
        private Queue<String> globalQueue = new ConcurrentLinkedQueue<String>();

        public void pathChanged(String path) {
            System.out.println("> Changed: " + path);
            globalQueue.add(path);
        }

        private List<String> getAllChanges() {
            return new ArrayList<String>(globalQueue);
        }
    }

    public ChangeCollector startWatch() {
        ChangeCollector collector = new ChangeCollector();
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.startWatch(collector, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get OSX memory info: %s", result.getMessage()));
        }
        return collector;
    }

    public List<String> stopWatch(ChangeCollector collector) {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.stopWatch(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get OSX memory info: %s", result.getMessage()));
        }
        return collector.getAllChanges();
    }
}
