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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegrationLinkageException;
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NativeLibraryLoader {
    private final Set<String> loaded = new HashSet<String>();
    private final Platform platform;
    private final NativeLibraryLocator nativeLibraryLocator;

    public NativeLibraryLoader(Platform platform, NativeLibraryLocator nativeLibraryLocator) {
        this.platform = platform;
        this.nativeLibraryLocator = nativeLibraryLocator;
    }

    public void load(String libraryFileName, List<String> platforms) {
        if (loaded.contains(libraryFileName)) {
            return;
        }
        try {
            UnsatisfiedLinkError loadFailure = null;
            for (String platformId : platforms) {
                File libFile = nativeLibraryLocator.find(new LibraryDef(libraryFileName, platformId));
                if (libFile == null) {
                    continue;
                }

                try {
                    System.load(libFile.getCanonicalPath());
                } catch (UnsatisfiedLinkError e) {
                    loadFailure = e;
                    continue;
                }

                loaded.add(libraryFileName);
                return;
            }
            if (loadFailure != null) {
                throw new NativeIntegrationLinkageException(String.format("Native library '%s' could not be loaded for %s.", libraryFileName, platform), loadFailure);
            }
            throw new NativeIntegrationUnavailableException(String.format("Native library '%s' is not available for %s.", libraryFileName, platform));
        } catch (NativeException e) {
            throw e;
        } catch (Throwable t) {
            throw new NativeException(String.format("Failed to load native library '%s' for %s.", libraryFileName, platform), t);
        }
    }
}
