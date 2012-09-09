package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.NativeLibraryLocator;
import net.rubygrapefruit.platform.internal.Platform;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
@ThreadSafe
public class Native {
    private static boolean loaded;
    private static final Map<Class<?>, Object> integrations = new HashMap<Class<?>, Object>();

    private Native() {
    }

    /**
     * Returns the version of the native library implementations used by this class.
     */
    @ThreadSafe
    static public String getNativeVersion() {
        return String.valueOf(NativeLibraryFunctions.VERSION);
    }

    /**
     * Initialises the native integration, if not already initialized.
     *
     * @param extractDir The directory to extract native resources into. May be null, in which case a default is
     * selected.
     */
    @ThreadSafe
    static public void init(File extractDir) {
        synchronized (Native.class) {
            if (!loaded) {
                Platform platform = Platform.current();
                try {
                    File libFile = new NativeLibraryLocator(extractDir).find(platform.getLibraryName());
                    if (libFile == null) {
                        throw new NativeIntegrationUnavailableException(String.format("Native library is not available for this operating system and architecture."));
                    }
                    System.load(libFile.getCanonicalPath());
                    int nativeVersion = NativeLibraryFunctions.getVersion();
                    if (nativeVersion != NativeLibraryFunctions.VERSION) {
                        throw new NativeException(String.format("Unexpected native library version loaded. Expected %s, was %s.", nativeVersion, NativeLibraryFunctions.VERSION));
                    }
                    loaded = true;
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
     * @return The native integration.
     * @throws NativeIntegrationUnavailableException When the given native integration is not available on the current
     * machine.
     * @throws NativeException On failure to load the native integration.
     */
    @ThreadSafe
    public static <T extends NativeIntegration> T get(Class<T> type)
            throws NativeIntegrationUnavailableException, NativeException {
        init(null);
        synchronized (Native.class) {
            Object instance = integrations.get(type);
            if (instance == null) {
                try {
                    instance = Platform.current().get(type);
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
}
