package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultOsxFileEventFunctions implements NativeIntegration {
    public boolean addRecursiveWatch(File path) throws IOException {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.createWatch(path.getCanonicalPath(), result);
        if (result.isFailed()) {
            throw new NativeException("Failed to start watching " + path.getCanonicalPath());
        }
        return true;
    }

    interface ChangeCallback {
        @SuppressWarnings("unused")
            // invoked from native code
        void pathChanged(String path);
    }

    public class WatcherThread extends Thread implements ChangeCallback {

        Queue<String> globalQueue = new ConcurrentLinkedQueue<String>();
        int status = 34;

        @Override
        public void run() {
            super.run();
            while (true) {
                if (status == -1) break;
                else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        public void pathChanged(String path) {
            globalQueue.add(path);
        }

        public List<String> getAllChanges() {
            List<String> changes = new ArrayList<String>(globalQueue.size());
            for (String s : globalQueue) {
                changes.add(s);
            }
            globalQueue.clear();
            return changes;
        }


    }

    public WatcherThread startWatch() {
        WatcherThread watcherThread = new WatcherThread();
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.startWatch(watcherThread, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get OSX memory info: %s", result.getMessage()));
        }
        watcherThread.start();
        return watcherThread;
    }

    public List<String> stopWatch(WatcherThread watcherThread) throws InterruptedException {
        FunctionResult result = new FunctionResult();
        OsxFileEventFunctions.stopWatch(result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get OSX memory info: %s", result.getMessage()));
        }
        watcherThread.status = -1;
        watcherThread.join();
        return  watcherThread.getAllChanges();

    }

}
