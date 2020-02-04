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
import net.rubygrapefruit.platform.internal.Platform
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

import static org.junit.Assume.assumeFalse

abstract class FilesTest extends AbstractFilesTest {
    @Shared
    def names = maybeWithUnicde([
            "test.txt",
            "test\u03b1\u2295.txt",
            "nested/test",
            // Long name
            (0..25).inject("") { s, v -> s + "/1234567890" }
    ])
    @Rule
    TemporaryFolder tmpDir
    final def files = Native.get(Files.class)

    void assertIsFile(FileInfo stat, File file) {
        assert stat.type == FileInfo.Type.File
        assert stat.size == file.length()
        def attributes = attributes(file)
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, file.lastModified())
    }

    void assertIsFile(DirEntry entry, File file, String name = file.name) {
        assert entry.type == FileInfo.Type.File
        assert entry.name == name
        assert entry.size == file.length()
        def attributes = attributes(file)
        assertTimestampMatches(entry.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(entry.lastModifiedTime, file.lastModified())
    }

    void assertIsDirectory(FileInfo stat, File file) {
        assert stat.type == FileInfo.Type.Directory
        assert stat.size == 0
        def attributes = attributes(file)
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, file.lastModified())
    }

    void assertIsDirectory(DirEntry entry, File file) {
        assert entry.type == FileInfo.Type.Directory
        assert entry.name == file.name
        assert entry.size == 0
        def attributes = attributes(file)
        assertTimestampMatches(entry.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(entry.lastModifiedTime, file.lastModified())
    }

    void assertIsSymlink(FileInfo stat, File file) {
        assert stat.type == FileInfo.Type.Symlink
        assert stat.size == 0
        def attributes = attributes(file)
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        // Can't check `file.lastModified()` as it follows symlinks
    }

    void assertIsSymlink(DirEntry entry, File file) {
        assert entry.type == FileInfo.Type.Symlink
        assert entry.name == file.name
        assert entry.size == 0
        def attributes = attributes(file)
        assertTimestampMatches(entry.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        // Can't check `file.lastModified()` as it follows symlinks
    }

    void assertIsMissing(FileInfo stat) {
        assert stat.type == FileInfo.Type.Missing
        assert stat.size == 0
        assert stat.lastModifiedTime == 0
    }

    void assertIsMissing(DirEntry entry, String name) {
        assert entry.type == FileInfo.Type.Missing
        assert entry.name == name
        assert entry.size == 0
        assert entry.lastModifiedTime == 0
    }

    def "caches file instance"() {
        expect:
        Native.get(Files.class) == files
    }

    @Unroll
    def "can stat a file"() {
        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.parentFile.mkdirs()
        testFile.text = 'hi'

        when:
        def stat = files.stat(testFile)

        then:
        assertIsFile(stat, testFile)
        stat.size == 2

        where:
        fileName << names
    }

    @Unroll
    def "follow links has no effect for stat of a file"() {
        def testFile = tmpDir.newFile("test.txt")
        testFile.text = 'hi'

        when:
        def stat = files.stat(testFile, followLinks)

        then:
        assertIsFile(stat, testFile)

        where:
        followLinks << [true, false]
    }

    @Unroll
    def "can stat a directory"() {
        def dir = tmpDir.newFolder()
        def testDir = new File(dir, fileName)
        testDir.mkdirs()

        when:
        def stat = files.stat(testDir)

        then:
        assertIsDirectory(stat, testDir)

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    @Unroll
    def "can stat a directory symbolic link"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testDir = new File(dir, fileName)
        testDir.mkdirs()

        def testLink = new File(dir, fileName + ".link")
        createDirectorySymbolicLink(testLink, testDir.name)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        assertIsSymlink(statLink, testLink)
        assertIsDirectory(statFile, testDir)

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    @Unroll
    def "can stat a file symbolic link"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.parentFile.mkdirs()
        testFile.text = 'hi'

        def testLink = new File(dir, fileName + ".link")
        createFileSymbolicLink(testLink, testFile.name)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        assertIsSymlink(statLink, testLink)
        assertIsFile(statFile, testFile)

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    @Unroll
    def "can stat a missing symbolic link"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)

        def testLink = new File(dir, fileName + ".link")
        testLink.parentFile.mkdirs()
        createFileSymbolicLink(testLink, testFile.name)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        assertIsSymlink(statLink, testLink)
        assertIsMissing(statFile)

        where:
        fileName << names
    }

    @Unroll
    def "follow links has no effect for stat of a directory"() {
        def testDir = tmpDir.newFolder("test.txt")

        when:
        def stat = files.stat(testDir, followLinks)

        then:
        assertIsDirectory(stat, testDir)

        where:
        followLinks << [true, false]
    }

    @Unroll
    def "can stat the file system roots reported by JVM"() {
        expect:
        def stat = files.stat(file)
        stat.type == (file.exists() ? FileInfo.Type.Directory : FileInfo.Type.Missing)
        stat.size == 0

        where:
        file << File.listRoots()
    }

    @Unroll
    def "can stat the file system roots reported by OS"() {
        expect:
        def stat
        try {
            stat = files.stat(fileSystem.mountPoint)
        } catch (FilePermissionException e) {
            // This is ok
            return
        }
        def fileType = fileSystem.mountPoint.absolutePath == '/run/docker/netns/default' ? FileInfo.Type.File : FileInfo.Type.Directory
        stat.type == (fileSystem.mountPoint.exists() ? fileType : FileInfo.Type.Missing)
        stat.size == 0

        where:
        fileSystem << Native.get(FileSystems.class).fileSystems
    }

    @Unroll
    def "can stat a missing file"() {
        def testFile = new File(tmpDir.root, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        assertIsMissing(stat)

        where:
        fileName << names
    }

    @Unroll
    def "can stat a missing file when something in the path matches an existing file = #dirName"() {
        def testDir = tmpDir.newFile(dirName)
        def testFile = new File(testDir, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        assertIsMissing(stat)

        where:
        dirName                                 | fileName
        "test-dir"                              | "test-file"
        maybeWithUnicde("test\u03b1\u2295-dir") | "test-file"
        "test-dir1"                             | "test-dir2/test-file"
    }

    @Unroll
    def "follow links has no effect for stat of a missing file"() {
        def testFile = new File(tmpDir.root, "nested/missing")

        when:
        def stat = files.stat(testFile, followLinks)

        then:
        assertIsMissing(stat)

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

    @Unroll
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

    @Unroll
    def "can list contents of a directory"() {
        def dir = tmpDir.newFolder()
        def testDir = new File(dir, fileName)
        testDir.mkdirs()

        def childDir = new File(testDir, testDir.name + ".a")
        childDir.mkdirs()
        def childFile = new File(testDir, testDir.name + ".b")
        childFile.text = 'contents'

        when:
        def files = files.listDir(testDir)

        then:
        files.size() == 2
        files.sort { it.name }

        def dirEntry = files[0]
        assertIsDirectory(dirEntry, childDir)

        def fileEntry = files[1]
        assertIsFile(fileEntry, childFile)
        fileEntry.size == 8

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    @Unroll
    def "can list contents of a directory with symbolic links"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()

        def childDir = new File(testFile, testFile.name + ".a")
        childDir.mkdirs()
        def childFile = new File(testFile, testFile.name + ".b")
        childFile.text = 'contents'

        def childLink = new File(testFile, testFile.name + ".link")
        createFileSymbolicLink(childLink, childFile.name)

        def childMissingLink = new File(testFile, testFile.name + ".missing.link")
        createFileSymbolicLink(childMissingLink, "missing")

        when:
        def files = files.listDir(testFile, false)

        then:
        files.size() == 4
        files.sort { it.name }

        def dirEntry = files[0]
        assertIsDirectory(dirEntry, childDir)

        def fileEntry = files[1]
        assertIsFile(fileEntry, childFile)

        def linkEntry = files[2]
        assertIsSymlink(linkEntry, childLink)

        def missingEntry = files[3]
        assertIsSymlink(missingEntry, childMissingLink)

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    @Unroll
    def "can list contents of a directory with symbolic links and follow links option"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()

        def childDir = new File(testFile, testFile.name + ".a")
        childDir.mkdirs()
        def childFile = new File(testFile, testFile.name + ".b")
        childFile.text = 'contents'

        def childLink = new File(testFile, testFile.name + ".link")
        createFileSymbolicLink(childLink, childFile.name)

        def childMissingLink = new File(testFile, testFile.name + ".missing.link")
        createFileSymbolicLink(childMissingLink, "missing")

        when:
        def files = files.listDir(testFile, true)

        then:
        files.size() == 4
        files.sort { it.name }

        def dirEntry = files[0]
        assertIsDirectory(dirEntry, childDir)

        def fileEntry = files[1]
        assertIsFile(fileEntry, childFile)

        def linkEntry = files[2]
        assertIsFile(linkEntry, childFile, childLink.name)

        def missingEntry = files[3]
        assertIsMissing(missingEntry, childMissingLink.name)

        where:
        fileName << names
    }

    def "follow links has no effect on list contents of a directory when the directory does not contain links"() {
        def testFile = tmpDir.newFolder("test.dir")
        def childDir = new File(testFile, "dir.a")
        childDir.mkdirs()
        def childFile = new File(testFile, "file.b")
        childFile.text = 'contents'

        when:
        def files = files.listDir(testFile, followLinks)

        then:
        files.size() == 2
        files.sort { it.name }

        def dirEntry = files[0]
        assertIsDirectory(dirEntry, childDir)

        def fileEntry = files[1]
        assertIsFile(fileEntry, childFile)

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

    @Unroll
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
