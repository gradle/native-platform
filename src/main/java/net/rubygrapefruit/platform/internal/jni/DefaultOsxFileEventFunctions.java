package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultOsxFileEventFunctions implements NativeIntegration {

    public boolean addRecursiveWatch(String... paths) {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.createWatch(paths, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watching " + Arrays.toString(paths) + ". Reason: " + result.getMessage());
        }
        return true;
    }

    public static class ChangeCollector implements OsxFileEventFunctions.ChangeCallback {
        private Queue<String> globalQueue = new ConcurrentLinkedQueue<String>();

        public void pathChanged(String path) {
            System.out.println("> Changed: " + path);
            globalQueue.add(path);
        }

        private List<String> getAllChanges() {
            Set<String> seen =  new HashSet<String>((int) (globalQueue.size() / 0.75f) + 1, 0.75f);
            List<String> result = new ArrayList<String>(globalQueue.size());
            while (true) {
                String path = globalQueue.poll();
                if (path == null) {
                    break;
                }
                if (seen.add(path)) {
                    result.add(path);
                }
            }
            return result;
        }
    }

    public ChangeCollector startWatch() {
        ChangeCollector collector = new ChangeCollector();
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.startWatch(collector, result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start collecting changes. Reason: " + result.getMessage());
        }
        return collector;
    }

    public List<String> stopWatch(ChangeCollector collector) {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.stopWatch(result);
        if (result.isFailed()) {
            throw new NativeException("Failed to get changed files. Reason: " + result.getMessage());
        }
        return collector.getAllChanges();
    }
}
