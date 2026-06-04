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

package net.rubygrapefruit.platform.file;

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.NativeIntegration;
import net.rubygrapefruit.platform.ThreadSafe;

import java.io.File;
import java.util.List;

/**
 * Provides access to the file systems of the current machine.
 */
@ThreadSafe
public interface FileSystems extends NativeIntegration {
    /**
     * Returns the set of all file systems for the current machine.
     *
     * @return The set of file systems. Never returns null.
     * @throws NativeException On failure.
     */
    @ThreadSafe
    List<FileSystemInfo> getFileSystems() throws NativeException;

    /**
     * Returns whether the file system backing the given path is a remote (network) file system.
     *
     * <p>This resolves only the volume containing {@code path}; unlike {@link #getFileSystems()} it
     * does not enumerate or probe the other volumes on the machine.</p>
     *
     * <p>The result matches the {@link FileSystemInfo#isRemote()} value of the corresponding
     * {@link #getFileSystems()} entry. Remoteness is only determined on platforms where
     * {@code getFileSystems()} reports it (currently Windows and macOS/BSD); on Linux this always
     * returns {@code false}, mirroring {@code getFileSystems()}, since network file systems there
     * are distinguished by their type rather than a remote flag.</p>
     *
     * @param path The path whose backing file system to inspect.
     * @return {@code true} if the path is on a remote file system.
     * @throws NativeException When the volume backing the path cannot be resolved.
     */
    @ThreadSafe
    boolean isRemote(File path) throws NativeException;
}
