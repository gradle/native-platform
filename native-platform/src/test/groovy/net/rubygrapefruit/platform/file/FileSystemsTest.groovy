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

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.Native
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class FileSystemsTest extends Specification {
    private static final List<String> EXPECTED_FILE_SYSTEM_TYPES = [
        // APFS on macOS
        'apfs',
        // HFS and HFS+ on macOS
        'hfs',
        'ext3',
        'ext4',
        'btrfs',
        'xfs',
        // FreeBSD
        'ufs',
        // NTFS on Windows
        'NTFS'
    ]

    @Rule TemporaryFolder tmpDir

    final FileSystems fileSystems = Native.get(FileSystems.class)

    def "caches file systems instance"() {
        expect:
        Native.get(FileSystems.class) == fileSystems
    }

    def "can query filesystem details"() {
        when:
        def mountedFileSystems = fileSystems.fileSystems
        then:
        mountedFileSystems.collect() { it.mountPoint }.containsAll(File.listRoots())
        mountedFileSystems.every { it.caseSensitivity != null }
        mountedFileSystems.any { EXPECTED_FILE_SYSTEM_TYPES.contains(it.fileSystemType) }
    }
}
