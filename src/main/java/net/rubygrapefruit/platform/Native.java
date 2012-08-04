package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.*;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

import java.io.File;
import java.io.IOException;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
public class Native {
    private static final Object lock = new Object();
    private static boolean loaded;

    static <T extends NativeIntegration> T get(Class<T> type) {
        Platform platform = Platform.current();
        synchronized (lock) {
            if (!loaded) {
                if (!platform.isSupported()) {
                    throw new NativeException(String.format("The current platform is not supported."));
                }
                try {
                    File libFile = new File("build/binaries/" + platform.getLibraryName());
                    System.load(libFile.getCanonicalPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                int nativeVersion = NativeLibraryFunctions.getVersion();
                if (nativeVersion != NativeLibraryFunctions.VERSION) {
                    throw new NativeException(String.format(
                            "Unexpected native library version loaded. Expected %s, was %s.", nativeVersion,
                            NativeLibraryFunctions.VERSION));
                }
                loaded = true;
            }
        }
        if (platform.isPosix()) {
            if (type.equals(PosixFile.class)) {
                return type.cast(new DefaultPosixFile());
            }
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new TerminfoTerminalAccess());
            }
        } else if (platform.isWindows()) {
            if (type.equals(Process.class)) {
                return type.cast(new DefaultProcess());
            }
            if (type.equals(TerminalAccess.class)) {
                return type.cast(new WindowsTerminalAccess());
            }
        }
        throw new UnsupportedOperationException(String.format("Cannot load unsupported native integration %s.",
                type.getName()));
    }
}
