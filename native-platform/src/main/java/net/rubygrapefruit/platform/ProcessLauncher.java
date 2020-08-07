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

import java.lang.Process;

/**
 * Used to start processes, taking care of some platform-specific issues when launching processes concurrently or
 * launching processes that will run in the background.
 */
@ThreadSafe
public interface ProcessLauncher extends NativeIntegration {
    /**
     * Starts a process from the given settings.
     *
     * @param processBuilder The process settings.
     * @return the process
     * @throws NativeException On failure to start the process.
     */
    @ThreadSafe
    Process start(ProcessBuilder processBuilder) throws NativeException;
}
