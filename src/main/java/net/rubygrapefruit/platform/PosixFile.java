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

import java.io.File;

/**
 * Functions to query and modify a file's POSIX meta-data.
 */
@ThreadSafe
public interface PosixFile extends NativeIntegration {
    /**
     * Sets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void setMode(File path, int perms) throws NativeException;

    /**
     * Gets the mode for the given file.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    int getMode(File path) throws NativeException;

    /**
     * Creates a symbolic link.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void symlink(File link, String contents) throws NativeException;

    /**
     * Reads the contents of a symbolic link.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    String readLink(File link) throws NativeException;
}
