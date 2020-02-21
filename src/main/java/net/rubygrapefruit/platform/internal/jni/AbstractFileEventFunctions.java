package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;

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
        List<String> canonicalPaths = canonicalizeAbsolutePaths(paths);
        return starter.createWatcher(
            canonicalPaths.toArray(new String[0]),
            new NativeFileWatcherCallback(callback)
        );
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
        FileWatcher createWatcher(String[] canonicalPaths, NativeFileWatcherCallback callback);
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
            stop(details);
            details = null;
        }

        protected abstract void stop(Object details);
    }

    protected static class NativeFileWatcherCallback {
        private final FileWatcherCallback delegate;

        public NativeFileWatcherCallback(FileWatcherCallback delegate) {
            this.delegate = delegate;
        }

        // Called from the native side
        @SuppressWarnings("unused")
        public void pathChanged(int type, String path) {
            delegate.pathChanged(FileWatcherCallback.Type.values()[type], path);
        }
    }
}
