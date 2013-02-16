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

    /**
     * Get the value of an environment variable.
     *
     * @return The value or null if no such environment variable. Also returns null for an environment variable whose
     *         value is an empty string.
     * @throws NativeException On failure.
     */
    @ThreadSafe
    String getEnvironmentVariable(String name) throws NativeException;

    /**
     * Sets the value of an environment variable.
     *
     * @param value the new value. Use null or an empty string to remove the environment variable. Note that on some
     * platforms it is not possible to remove the environment variable safely. On such platforms, the value is set to an
     * empty string instead.
     * @throws NativeException On failure.
     */
    @ThreadSafe
    void setEnvironmentVariable(String name, String value) throws NativeException;
}
