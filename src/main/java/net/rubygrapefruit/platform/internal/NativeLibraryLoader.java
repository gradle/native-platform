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
