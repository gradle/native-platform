package net.rubygrapefruit.platform.file;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.ThreadSafe;
import net.rubygrapefruit.platform.internal.NativeLibraryLoader;
import net.rubygrapefruit.platform.internal.NativeLibraryLocator;
import net.rubygrapefruit.platform.internal.Platform;
import net.rubygrapefruit.platform.internal.jni.AbstractNativeFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.FileEventsVersion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FileEventsRust {
    private static NativeLibraryLoader loader;
    private static final Map<Class<?>, Object> integrations = new HashMap<Class<?>, Object>();

    private FileEventsRust() {
    }

    @ThreadSafe
    static public void init(File extractDir) throws NativeIntegrationUnavailableException, NativeException {
        synchronized (FileEventsRust.class) {
            if (loader == null) {
                Platform platform = Platform.current();
                try {
                    loader = new NativeLibraryLoader(platform, new NativeLibraryLocator(extractDir, FileEventsVersion.VERSION));
                    loader.load(determineLibraryName(platform), platform.getLibraryVariants());
                    String nativeVersion = AbstractNativeFileEventFunctions.getVersion();
                    if (!nativeVersion.equals(FileEventsVersion.VERSION)) {
                        throw new NativeException(String.format(
                            "Unexpected native file events library version loaded. Expected %s, was %s.",
                            nativeVersion,
                            FileEventsVersion.VERSION
                        ));
                    }
                } catch (NativeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new NativeException("Failed to initialise native integration.", t);
                }
            }
        }
    }

    private static String determineLibraryName(Platform platform) {
        if (platform.isLinux()) {
            return "libfile_events_rust.so";
        }
        if (platform.isMacOs()) {
            return "libfile_events_rust.dylib";
        }
        if (platform.isWindows()) {
            return "libfile_events_rust.dll";
        }
        throw new NativeIntegrationUnavailableException(String.format("Native file events integration is not available for %s.", platform));
    }
}
