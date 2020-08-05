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

import net.rubygrapefruit.platform.ThreadSafe;

/**
 * Provides some information about a file. This is a snapshot and does not change.
 *
 * <p>A snapshot be fetched using {@link Files#stat(java.io.File)}.</p>
 */
@ThreadSafe
public interface FileInfo {
    // Order is significant here, see generic.h
    enum Type {
        File, Directory, Symlink, Other, Missing
    }

    /**
     * Returns the type of this file.
     */
    Type getType();

    /**
     * Returns the size of this file, in bytes. Returns 0 when this file is not a regular file.
     */
    long getSize();

    /**
     * Returns the last modification time of this file, in ms since epoch. Returns 0 when this file does not exist.
     */
    long getLastModifiedTime();
}
