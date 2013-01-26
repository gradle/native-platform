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
 * Functions to query and modify a process' state.
 */
@ThreadSafe
public interface Process extends NativeIntegration {
    /**
     * Returns the process identifier.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    int getProcessId() throws NativeException;

    /**
     * Returns the process' current working directory.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    File getWorkingDirectory() throws NativeException;

    /**
     * Sets the process' working directory.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void setWorkingDirectory(File directory) throws NativeException;
}
