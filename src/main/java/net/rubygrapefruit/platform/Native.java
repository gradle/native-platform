package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.*;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;

import java.io.*;
import java.net.URL;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
public class Native {
    private static final Object lock = new Object();
    private static boolean loaded;

    private Native() {
    }

    /**
     * Initialises the native integration, if not already initialized.
     *
     * @param extractDir The directory to extract native resources into. May be null, in which case a default is
     * selected.
     */
    static public void init(File extractDir) {
        synchronized (lock) {
            if (!loaded) {
                Platform platform = Platform.current();
                try {
                    File libFile;
                    URL resource = Native.class.getClassLoader().getResource(platform.getLibraryName());
                    if (resource != null) {
                        File libDir = extractDir;
                        if (libDir == null) {
                            libDir = File.createTempFile("native-platform", "dir");
                            libDir.delete();
                            libDir.mkdirs();
                        }
                        libFile = new File(libDir, platform.getLibraryName());
                        libFile.deleteOnExit();
                        copy(resource, libFile);
                    } else {
                        libFile = new File("build/binaries/" + platform.getLibraryName());
                    }
                    if (!libFile.isFile()) {
                        throw new NativeIntegrationUnavailableException(String.format("Native library is not available for this operating system and architecture."));
                    }
                    System.load(libFile.getCanonicalPath());
                    int nativeVersion = NativeLibraryFunctions.getVersion();
                    if (nativeVersion != NativeLibraryFunctions.VERSION) {
                        throw new NativeException(String.format(
                                "Unexpected native library version loaded. Expected %s, was %s.", nativeVersion,
                                NativeLibraryFunctions.VERSION));
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
    public static <T extends NativeIntegration> T get(Class<T> type)
            throws NativeIntegrationUnavailableException, NativeException {
        init(null);
        try {
            Platform platform = Platform.current();
            return platform.get(type);
        } catch (NativeException e) {
            throw e;
        } catch (Throwable t) {
            throw new NativeException(String.format("Failed to load native integration %s.", type.getSimpleName()), t);
        }
    }

    private static void copy(URL source, File dest) {
        try {
            InputStream inputStream = source.openStream();
            try {
                OutputStream outputStream = new FileOutputStream(dest);
                try {
                    byte[] buffer = new byte[4096];
                    while (true) {
                        int nread = inputStream.read(buffer);
                        if (nread < 0) {
                            break;
                        }
                        outputStream.write(buffer, 0, nread);
                    }
                } finally {
                    outputStream.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new NativeException(String.format("Could not extract native JNI library."), e);
        }
    }
}
