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

package net.rubygrapefruit.platform.internal

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.NativePlatformSpec
import spock.lang.IgnoreIf

@IgnoreIf({Platform.current().windows})
class NativeTypeInfoTest extends NativePlatformSpec {
    def "can fetch native type info"() {
        expect:
        MutableTypeInfo typeInfo = Native.get(MutableTypeInfo.class)
        println "int: ${typeInfo.int_bytes}"
        println "u_long: ${typeInfo.u_long_bytes}"
        println "size_t: ${typeInfo.size_t_bytes}"
        println "off_t: ${typeInfo.off_t_bytes}"
        println "uid_t: ${typeInfo.uid_t_bytes}"
        println "gid_t: ${typeInfo.gid_t_bytes}"
    }
}
