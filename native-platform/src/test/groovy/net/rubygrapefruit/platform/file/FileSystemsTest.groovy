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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import org.junit.jupiter.api.Assumptions
import spock.lang.Requires

class FileSystemsTest extends NativePlatformSpec {
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

    final FileSystems fileSystems = getIntegration(FileSystems)

    File tmpDir

    def setup() {
        tmpDir = File.createTempDir()
    }

    def cleanup() {
        tmpDir?.deleteDir()
    }

    def "caches file systems instance"() {
        expect:
        getIntegration(FileSystems) == fileSystems
    }

    def "isRemote is false for a local temp directory"() {
        expect:
        !fileSystems.isRemote(tmpDir)
    }

    def "isRemote agrees with getFileSystems for each file system root"() {
        given:
        def byMountPoint = fileSystems.fileSystems.collectEntries { [(it.mountPoint): it] }

        expect:
        File.listRoots().findAll { it.exists() && byMountPoint.containsKey(it) }.every { root ->
            fileSystems.isRemote(root) == byMountPoint[root].remote
        }
    }

    def "isRemote handles a non-ASCII path component"() {
        given:
        def dir = new File(tmpDir, "näme-ü")
        dir.mkdirs()

        expect:
        !fileSystems.isRemote(dir)
    }

    @Requires({ Platform.current().windows })
    def "isRemote handles a long path without crashing"() {
        given:
        def dir = tmpDir
        // Build a path well beyond MAX_PATH (260)
        20.times {
            dir = new File(dir, "long-path-segment-component-${it}")
        }
        dir.mkdirs()

        when:
        def remote = fileSystems.isRemote(dir)

        then:
        // Either resolves the (local) volume or fails cleanly; never crashes.
        noExceptionThrown()
        !remote
    }

    def "isRemote on a nonexistent path has defined behavior"() {
        given:
        def missing = new File(tmpDir, "does-not-exist")

        when:
        boolean threw = false
        boolean remote = false
        try {
            remote = fileSystems.isRemote(missing)
        } catch (NativeException ignored) {
            // On POSIX statfs fails for a missing path. Acceptable: a clean exception, not a crash.
            threw = true
        }

        then:
        // On Windows the volume still resolves for a missing path on an existing drive (-> false);
        // on POSIX it throws. Either is fine, neither crashes the JVM.
        threw || !remote
    }

    def "can query filesystem details"() {
        when:
        def mountedFileSystems = fileSystems.fileSystems
        then:
        mountedFileSystems.collect() { it.mountPoint }.containsAll(File.listRoots())
        mountedFileSystems.every { it.caseSensitivity != null || it.fileSystemType == "hfs" }
        mountedFileSystems.any { EXPECTED_FILE_SYSTEM_TYPES.contains(it.fileSystemType) }
    }


    @Requires({ Platform.current().linux })
    def "detects file systems of mount points correctly"() {
        def mountPoint = "/${fileSystemType}"
        Assumptions.assumeTrue(new File(mountPoint).exists(), "Mount point for ${fileSystemType} exists")

        when:
        def mountedFileSystems = fileSystems.fileSystems
        def specialFileSystem = mountedFileSystems.find { it.mountPoint.absolutePath == mountPoint }
        then:
        specialFileSystem.fileSystemType == fileSystemType

        where:
        fileSystemType << ["xfs", "btrfs"]
    }
}
