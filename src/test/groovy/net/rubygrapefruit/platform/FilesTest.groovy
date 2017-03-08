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

import net.rubygrapefruit.platform.internal.Platform
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Shared

class FilesTest extends AbstractFilesTest {
    @Shared
    def names = [
            "test.txt",
            "test\u03b1\u2295.txt",
            "nested/test",
            // Long name
            (0..25).inject("") { s, v -> s + "/1234567890" }
    ]
    @Rule TemporaryFolder tmpDir
    final def files = Native.get(Files.class)

    def "caches file instance"() {
        expect:
        Native.get(Files.class) == files
    }

    def "can stat a file"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.parentFile.mkdirs()
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
        fileName << names
    }

    def "follow links has no effect for stat of a file"() {
        def testFile = tmpDir.newFile("test.txt")
        testFile.text = 'hi'
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile, followLinks)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 2
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()

        where:
        followLinks << [true, false]
    }

    def "can stat a directory"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Directory
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()

        where:
        fileName << names
    }

    def "follow links has no effect for stat of a directory"() {
        def testFile = tmpDir.newFolder("test.txt")
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile, followLinks)

        then:
        stat.type == FileInfo.Type.Directory
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()

        where:
        followLinks << [true, false]
    }

    def "can stat the file system roots reported by JVM"() {
        expect:
        def stat = files.stat(file)
        stat.type == (file.exists() ? FileInfo.Type.Directory : FileInfo.Type.Missing)
        stat.size == 0

        where:
        file << File.listRoots()
    }

    def "can stat the file system roots reported by OS"() {
        expect:
        def stat = files.stat(fileSystem.mountPoint)
        stat.type == (fileSystem.mountPoint.exists() ? FileInfo.Type.Directory : FileInfo.Type.Missing)
        stat.size == 0

        where:
        fileSystem << Native.get(FileSystems.class).fileSystems
    }

    def "can stat a missing file"() {
        def testFile = new File(tmpDir.root, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing
        stat.size == 0
        stat.lastModifiedTime == 0

        where:
        fileName << names
    }

    def "follow links has no effect for stat of a missing file"() {
        def testFile = new File(tmpDir.root, "nested/missing")

        when:
        def stat = files.stat(testFile, followLinks)

        then:
        stat.type == FileInfo.Type.Missing
        stat.size == 0
        stat.lastModifiedTime == 0

        where:
        followLinks << [true, false]
    }

    def "can stat a changing file"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, "file")

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing

        when:
        testFile.text = "123"
        stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 3

        when:
        testFile.text = "1"
        stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 1

        when:
        testFile.delete()
        stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing

        when:
        testFile.mkdirs()
        stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Directory
    }

    def "can stat a renamed file"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, "file")
        testFile.text = "123"
        def other = new File(dir, "renamed")

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 3

        when:
        testFile.renameTo(other)
        stat = files.stat(other)

        then:
        stat.type == FileInfo.Type.File
        stat.size == 3
    }

    def "can stat relative paths"() {
        def thisDir = new File(".")
        def parentDir = new File("..")

        when:
        def stat = files.stat(thisDir)

        then:
        stat.type == FileInfo.Type.Directory

        when:
        stat = files.stat(parentDir)

        then:
        stat.type == FileInfo.Type.Directory

        when:
        stat = files.stat(new File("../../" + parentDir.canonicalFile.name + "/" + thisDir.canonicalFile.name))

        then:
        stat.type == FileInfo.Type.Directory
    }

    @IgnoreIf({ !Platform.current().windows })
    def "can stat file using UNC path"() {
        def file = tmpDir.newFile()
        def testFile = new File('\\\\localhost\\' + file.absolutePath.charAt(0) + '$\\' + file.absolutePath.substring(2))

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
    }

    def "can list contents of an empty directory"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()

        when:
        def files = files.listDir(testFile)

        then:
        files.size() == 0

        where:
        fileName << names
    }

    def "can list contents of a directory"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()

        def childDir = new File(testFile, testFile.name + ".a")
        childDir.mkdirs()
        def childDirAttributes = attributes(childDir)
        def childFile = new File(testFile, testFile.name + ".b")
        childFile.text = 'contents'
        def childFileAttributes = attributes(childFile)

        when:
        def files = files.listDir(testFile)

        then:
        files.size() == 2
        files.sort { it.name }

        def dirEntry = files[0]
        dirEntry.type == FileInfo.Type.Directory
        dirEntry.name == childDir.name
        dirEntry.size == 0L
        dirEntry.lastModifiedTime == childDirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(dirEntry.lastModifiedTime) == childDir.lastModified()

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        fileEntry.lastModifiedTime == childFileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(fileEntry.lastModifiedTime) == childFile.lastModified()

        where:
        fileName << names
    }

    def "follow links has no effect on list contents of a directory when the directory does not contain links"() {
        def testFile = tmpDir.newFolder("test.dir")
        def childDir = new File(testFile, "dir.a")
        childDir.mkdirs()
        def childDirAttributes = attributes(childDir)
        def childFile = new File(testFile, "file.b")
        childFile.text = 'contents'
        def childFileAttributes = attributes(childFile)

        when:
        def files = files.listDir(testFile, followLinks)

        then:
        files.size() == 2
        files.sort { it.name }

        def dirEntry = files[0]
        dirEntry.type == FileInfo.Type.Directory
        dirEntry.name == childDir.name
        dirEntry.size == 0L
        dirEntry.lastModifiedTime == childDirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(dirEntry.lastModifiedTime) == childDir.lastModified()

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        fileEntry.lastModifiedTime == childFileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(fileEntry.lastModifiedTime) == childFile.lastModified()

        where:
        followLinks << [true, false]
    }

    def "cannot list contents of file"() {
        def testFile = tmpDir.newFile()

        when:
        files.listDir(testFile)

        then:
        def e = thrown(NotADirectoryException)
        e.message == "Could not list directory $testFile as it is not a directory."
    }

    def "cannot list contents of missing file"() {
        def testFile = new File(tmpDir.newFolder(), fileName)

        when:
        files.listDir(testFile)

        then:
        def e = thrown(NoSuchFileException)
        e.message == "Could not list directory $testFile as this directory does not exist."

        where:
        fileName << names
    }

}
