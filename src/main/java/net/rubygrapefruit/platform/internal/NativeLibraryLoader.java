package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class NativeLibraryLoader {
    private final Set<String> loaded = new HashSet<String>();
    private final NativeLibraryLocator locator;

    public NativeLibraryLoader(NativeLibraryLocator locator) {
        this.locator = locator;
    }

    public void load(String name) {
        if (loaded.contains(name)) {
            return;
        }
        try {
            File libFile = locator.find(name);
            if (libFile == null) {
                throw new NativeIntegrationUnavailableException(String.format(
                        "Native library is not available for this operating system and architecture."));
            }
            System.load(libFile.getCanonicalPath());
        } catch (NativeException e) {
            throw e;
        } catch (Throwable t) {
            throw new NativeException(String.format("Failed to load native library '%s'.", name), t);
        }
        loaded.add(name);
    }
}
