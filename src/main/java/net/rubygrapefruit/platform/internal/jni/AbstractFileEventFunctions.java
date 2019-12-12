package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;
import net.rubygrapefruit.platform.internal.FunctionResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AbstractFileEventFunctions implements NativeIntegration {
    protected FileWatcher createWatcher(Collection<String> paths, FileWatcherCallback callback, WatcherFactory starter) {
        if (paths.isEmpty()) {
            return FileWatcher.EMPTY;
        }
        FunctionResult result = new FunctionResult();
        List<String> canonicalPaths = canonicalizeAbsolutePaths(paths);
        FileWatcher watcher = starter.createWatcher(
            canonicalPaths.toArray(new String[0]),
            new NativeFileWatcherCallback(callback),
            result
        );
        if (result.isFailed()) {
            throw new NativeException("Failed to start watching. Reason: " + result.getMessage());
        }
        return watcher;
    }

    /**
     * Canonicalizes the given paths using {@link File#getCanonicalPath()}.
     * Throws {@link NativeException} if any of the given paths is not absolute,
     * or if they cannot be canonicalized for any reason.
     */
    private static List<String> canonicalizeAbsolutePaths(Collection<String> watchRoots) {
        List<String> canonicalPaths = new ArrayList<String>(watchRoots.size());
        for (String watchRoot : watchRoots) {
            File fileRoot = new File(watchRoot);
            if (!fileRoot.isAbsolute()) {
                throw new NativeException("Watched root is not absolute: " + fileRoot);
            }
            try {
                canonicalPaths.add(fileRoot.getCanonicalPath());
            } catch (IOException ex) {
                throw new NativeException("Couldn't resolve canonical path for: " + watchRoot, ex);
            }
        }
        return canonicalPaths;
    }

    protected interface WatcherFactory {
        FileWatcher createWatcher(String[] canonicalPaths, NativeFileWatcherCallback callback, FunctionResult result);
    }

    protected static abstract class AbstractFileWatcher implements FileWatcher {
        /**
         * Details is a Java object wrapper around whatever data the native implementation
         * needs to keep track of.
         */
        private Object details;

        public AbstractFileWatcher(Object details) {
            this.details = details;
        }

        @Override
        public void close() {
            if (details == null) {
                return;
            }
            FunctionResult result = new FunctionResult();
            stop(details, result);
            details = null;
            if (result.isFailed()) {
                throw new NativeException("Failed to stop watching. Reason: " + result.getMessage());
            }
        }

        protected abstract void stop(Object details, FunctionResult result);
    }

    protected static class NativeFileWatcherCallback {
        private final FileWatcherCallback delegate;

        public NativeFileWatcherCallback(FileWatcherCallback delegate) {
            this.delegate = delegate;
        }

        public void pathChanged(int type, String path) {
            delegate.pathChanged(FileWatcherCallback.Type.values()[type], path);
        }
    }
}
