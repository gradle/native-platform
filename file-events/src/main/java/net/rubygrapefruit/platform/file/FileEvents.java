package net.rubygrapefruit.platform.file;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.ThreadSafe;
import net.rubygrapefruit.platform.internal.NativeLibraryLoader;
import net.rubygrapefruit.platform.internal.NativeLibraryLocator;
import net.rubygrapefruit.platform.internal.Platform;
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.FileEventsVersion;
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@ThreadSafe
public class FileEvents {
    private static NativeLibraryLoader loader;
    private static final Map<Class<?>, Object> integrations = new HashMap<Class<?>, Object>();

    private FileEvents() {
    }

    @ThreadSafe
    static public void init(File extractDir) throws NativeIntegrationUnavailableException, NativeException {
        synchronized (FileEvents.class) {
            if (loader == null) {
                Platform platform = Platform.current();
                try {
                    loader = new NativeLibraryLoader(platform, new NativeLibraryLocator(extractDir, FileEventsVersion.VERSION));
                    loader.load(determineLibraryName(platform), platform.getLibraryVariants());
                    String nativeVersion = AbstractFileEventFunctions.getVersion();
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

    /**
     * Locates a native integration of the given type.
     *
     * @return The native integration. Never returns null.
     * @throws NativeIntegrationUnavailableException When the given native integration is not available on the current
     * machine.
     * @throws NativeException On failure to load the native integration.
     */
    @ThreadSafe
    public static <T extends NativeIntegration> T get(Class<T> type)
        throws NativeIntegrationUnavailableException, NativeException {
        init(null);
        synchronized (FileEvents.class) {
            Platform platform = Platform.current();
            Object instance = integrations.get(type);
            if (instance == null) {
                try {
                    instance = getEventFunctions(type, platform);
                } catch (NativeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new NativeException(String.format("Failed to load native integration %s.", type.getSimpleName()), t);
                }
                integrations.put(type, instance);
            }
            return type.cast(instance);
        }
    }

    private static <T extends NativeIntegration> T getEventFunctions(Class<T> type, Platform platform) {
        if (platform.isWindows() && type.equals(WindowsFileEventFunctions.class)) {
            return type.cast(new WindowsFileEventFunctions());
        }
        if (platform.isLinux() && type.equals(LinuxFileEventFunctions.class)) {
            return type.cast(new LinuxFileEventFunctions());
        }
        if (platform.isMacOs() && type.equals(OsxFileEventFunctions.class)) {
            return type.cast(new OsxFileEventFunctions());
        }
        throw new NativeIntegrationUnavailableException(String.format(
            "Native integration %s is not supported for %s.",
            type.getSimpleName(), platform.toString())
        );
    }

    private static String determineLibraryName(Platform platform) {
        if (platform.isLinux()) {
            return "libnative-platform-file-events.so";
        }
        if (platform.isMacOs()) {
            return "libnative-platform-file-events.dylib";
        }
        if (platform.isWindows()) {
            return "native-platform-file-events.dll";
        }
        throw new NativeIntegrationUnavailableException(String.format("Native file events integration is not available for %s.", platform));
    }
}
