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

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes

class FilesTest extends AbstractFilesTest {
    @Rule TemporaryFolder tmpDir
    final def files = Native.get(Files.class)

    def "caches file instance"() {
        expect:
        Native.get(Files.class) == files
    }

    def "can get details of a file"() {
        def testFile = tmpDir.newFile(fileName)
        testFile.text = 'hi'
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 2
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can get details of a directory"() {
        def testFile = tmpDir.newFolder(fileName)
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Directory
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "can get details of file system roots reported by JVM"() {
        expect:
        def stat = files.stat(file)
        stat.type == (file.exists() ? FileInfo.Type.Directory : FileInfo.Type.Missing)
        stat.size == 0

        where:
        file << File.listRoots()
    }

    def "can get details of file system roots reported by OS"() {
        expect:
        def stat = files.stat(fileSystem.mountPoint)
        stat.type == (fileSystem.mountPoint.exists() ? FileInfo.Type.Directory : FileInfo.Type.Missing)
        stat.size == 0

        where:
        fileSystem << Native.get(FileSystems.class).fileSystems
    }

    def "can get details of a missing file"() {
        def testFile = new File(tmpDir.root, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing
        stat.size == 0
        stat.lastModifiedTime == 0

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir", "nested/dir"]
    }

    BasicFileAttributes attributes(File file) {
        return java.nio.file.Files.getFileAttributeView(file.toPath(), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes()
    }
}
