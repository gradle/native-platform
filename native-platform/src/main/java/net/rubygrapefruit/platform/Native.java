/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform;

import net.rubygrapefruit.platform.internal.NativeLibraryLoader;
import net.rubygrapefruit.platform.internal.NativeLibraryLocator;
import net.rubygrapefruit.platform.internal.Platform;
import net.rubygrapefruit.platform.internal.jni.NativeLibraryFunctions;
import net.rubygrapefruit.platform.internal.jni.NativeVersion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides access to the native integrations. Use {@link #get(Class)} to load a particular integration.
 */
@ThreadSafe
public class Native {
    private static NativeLibraryLoader loader;
    private static final Map<Class<?>, Object> integrations = new HashMap<Class<?>, Object>();

    private Native() {
    }

    /**
     * Initialises the native integration, if not already initialized.
     *
     * @param extractDir The directory to extract native resources into.
     *
     * @throws NativeIntegrationUnavailableException When native integration is not available on the current machine.
     * @throws NativeException On failure to load the native integration.
     */
    @ThreadSafe
    static public void init(File extractDir) throws NativeIntegrationUnavailableException, NativeException {
        synchronized (Native.class) {
            if (loader != null) {
                throw new NativeException("Native integration already initialised.");
            }
            Platform platform = Platform.current();
            try {
                loader = new NativeLibraryLoader(platform, new NativeLibraryLocator(extractDir, NativeVersion.VERSION));
                loader.load(platform.getLibraryName(), platform.getLibraryVariants());
                String nativeVersion = NativeLibraryFunctions.getVersion();
                if (!nativeVersion.equals(NativeVersion.VERSION)) {
                    throw new NativeException(String.format("Unexpected native library version loaded. Expected %s, was %s.", NativeVersion.VERSION, nativeVersion));
                }
            } catch (NativeException e) {
                throw e;
            } catch (Throwable t) {
                throw new NativeException("Failed to initialise native integration.", t);
            }
        }
    }

    /**
     * Locates a native integration of the given type.
     * Must call {@link #init(File)} before calling this method.
     *
     * @return The native integration. Never returns null.
     * @throws NativeIntegrationUnavailableException When the given native integration is not available on the current
     * machine.
     * @throws NativeException On failure to load the native integration, or if {@link #init(File)} hasn't been called before.
     */
    @ThreadSafe
    public static <T extends NativeIntegration> T get(Class<T> type)
        throws NativeIntegrationUnavailableException, NativeException {
        synchronized (Native.class) {
            if (loader == null) {
                throw new NativeException("Native integration not initialised.");
            }
            Platform platform = Platform.current();
            Class<? extends T> canonicalType = platform.canonicalise(type);
            Object instance = integrations.get(canonicalType);
            if (instance == null) {
                try {
                    instance = platform.get(canonicalType, loader);
                } catch (NativeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new NativeException(String.format("Failed to load native integration %s.", type.getSimpleName()), t);
                }
                integrations.put(canonicalType, instance);
            }
            return type.cast(instance);
        }
    }
}
