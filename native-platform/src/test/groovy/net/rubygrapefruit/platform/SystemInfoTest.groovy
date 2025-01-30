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

package net.rubygrapefruit.platform

class SystemInfoTest extends NativePlatformSpec {
    final SystemInfo systemInfo = Native.get(SystemInfo.class)

    def "caches system info instance"() {
        expect:
        Native.get(SystemInfo.class) == systemInfo
    }

    def "can query OS details"() {
        expect:
        systemInfo.kernelName
        systemInfo.kernelVersion
        systemInfo.architectureName
        systemInfo.architecture
        systemInfo.hostname
    }
}
