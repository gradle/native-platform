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

/**
 * Provides access to some system information. This is a snapshot view and does not change.
 */
@ThreadSafe
public interface SystemInfo extends NativeIntegration {
    /**
     * Returns the name of the kernel for the current operating system.
     */
    @ThreadSafe
    String getKernelName();

    /**
     * Returns the version of the kernel for the current operating system.
     */
    @ThreadSafe
    String getKernelVersion();

    /**
     * Returns the machine architecture, as reported by the operating system.
     */
    @ThreadSafe
    String getMachineArchitecture();
}
