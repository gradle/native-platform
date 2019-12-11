package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.NativeException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class CanonicalPathUtil {
    /**
     * Canonicalizes the given paths using {@link File#getCanonicalPath()}.
     * Throws {@link NativeException} if any of the given paths is not absolute,
     * or if they cannot be canonicalized for any reason.
     */
    public static List<String> canonicalizeAbsolutePaths(Collection<String> watchRoots) {
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
}
