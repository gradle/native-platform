package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NativeLibraryLoader {
    private final Set<String> loaded = new HashSet<String>();
    private final NativeLibraryLocator locator;

    public NativeLibraryLoader(NativeLibraryLocator locator) {
        this.locator = locator;
    }

    public void load(String name) throws IOException {
        if (loaded.contains(name)) {
            return;
        }
        File libFile = locator.find(name);
        if (libFile == null) {
            throw new NativeIntegrationUnavailableException(String.format(
                    "Native library is not available for this operating system and architecture."));
        }
        System.load(libFile.getCanonicalPath());
        loaded.add(name);
    }
}
