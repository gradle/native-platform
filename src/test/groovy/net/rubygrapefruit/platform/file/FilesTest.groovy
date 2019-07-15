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

import static org.junit.Assume.assumeFalse

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
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, testFile.lastModified())

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
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, testFile.lastModified())

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
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, testFile.lastModified())

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    def "can stat a directory symbolic link"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.mkdirs()
        def testFileAttributes = attributes(testFile)

        def testLink = new File(dir, fileName + ".link")
        createDirectorySymbolicLink(testLink, testFile.name)
        def testLinkAttributes = attributes(testLink)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        statLink.type == FileInfo.Type.Symlink
        statLink.size == 0
        assertTimestampMatches(statLink.lastModifiedTime, testLinkAttributes.lastModifiedTime().toMillis())
        // Note java.io.File.lastModified() follows symbolic links, so the following assertions is not verified
        //assertTimestampMatches(statLink.lastModifiedTime, testLink.lastModified())

        statFile.type == FileInfo.Type.Directory
        statFile.size == 0
        assertTimestampMatches(statFile.lastModifiedTime, testFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(statFile.lastModifiedTime, testFile.lastModified())

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
    def "can stat a file symbolic link"() {
        // We can't run this test with long paths on Windows, because the createDirectorySymbolicLink
        // and createFileSymbolicLink methods use the "mklink" command on that platform, and it is currently
        // limited to 260 character paths.
        assumeFalse(Platform.current().windows && fileName.size() > 260)

        def dir = tmpDir.newFolder()
        def testFile = new File(dir, fileName)
        testFile.parentFile.mkdirs()
        testFile.text = 'hi'
        def testFileAttributes = attributes(testFile)

        def testLink = new File(dir, fileName + ".link")
        createFileSymbolicLink(testLink, testFile.name)
        def testLinkAttributes = attributes(testLink)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        statLink.type == FileInfo.Type.Symlink
        statLink.size == 0
        assertTimestampMatches(statLink.lastModifiedTime, testLinkAttributes.lastModifiedTime().toMillis())
        // Note java.io.File.lastModified() follows symbolic links, so the following assertions is not verified
        //assertTimestampMatches(statLink.lastModifiedTime, testLink.lastModified())

        statFile.type == FileInfo.Type.File
        statFile.size == 2
        assertTimestampMatches(statFile.lastModifiedTime, testFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(statFile.lastModifiedTime, testFile.lastModified())

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
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
        def testLinkAttributes = attributes(testLink)

        when:
        def statLink = files.stat(testLink, false)
        def statFile = files.stat(testLink, true)

        then:
        statLink.type == FileInfo.Type.Symlink
        statLink.size == 0
        assertTimestampMatches(statLink.lastModifiedTime, testLinkAttributes.lastModifiedTime().toMillis())
        // Note java.io.File.lastModified() follows symbolic links, so the following assertions is not verified
        //assertTimestampMatches(statLink.lastModifiedTime, testLink.lastModified())

        statFile.type == FileInfo.Type.Missing
        statFile.size == 0
        statFile.lastModifiedTime == 0

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
        assertTimestampMatches(stat.lastModifiedTime, attributes.lastModifiedTime().toMillis())
        assertTimestampMatches(stat.lastModifiedTime, testFile.lastModified())

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
        def stat
        try {
            stat = files.stat(fileSystem.mountPoint)
        } catch (FilePermissionException e) {
            // This is ok
            return
        }
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

    def "can stat a missing file when something in the path matches an existing file"() {
        def testDir = tmpDir.newFile(dirName)
        def testFile = new File(testDir, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing
        stat.size == 0
        stat.lastModifiedTime == 0

        where:
        dirName                | fileName
        "test-dir"             | "test-file"
        "test\u03b1\u2295-dir" | "test-file"
        "test-dir1"            | "test-dir2/test-file"
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
        assertTimestampMatches(dirEntry.lastModifiedTime, childDirAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(dirEntry.lastModifiedTime, childDir.lastModified())

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        assertTimestampMatches(fileEntry.lastModifiedTime, childFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(fileEntry.lastModifiedTime, childFile.lastModified())

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
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
        def childDirAttributes = attributes(childDir)
        def childFile = new File(testFile, testFile.name + ".b")
        childFile.text = 'contents'
        def childFileAttributes = attributes(childFile)

        def childLink = new File(testFile, testFile.name + ".link")
        createFileSymbolicLink(childLink, childFile.name)
        def childLinkAttributes = attributes(childLink)

        def childMissingLink = new File(testFile, testFile.name + ".missing.link")
        createFileSymbolicLink(childMissingLink, "missing")
        def childMissingLinkAttributes = attributes(childMissingLink)

        when:
        def files = files.listDir(testFile, false)

        then:
        files.size() == 4
        files.sort { it.name }

        def dirEntry = files[0]
        dirEntry.type == FileInfo.Type.Directory
        dirEntry.name == childDir.name
        dirEntry.size == 0L
        assertTimestampMatches(dirEntry.lastModifiedTime, childDirAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(dirEntry.lastModifiedTime, childDir.lastModified())

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        assertTimestampMatches(fileEntry.lastModifiedTime, childFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(fileEntry.lastModifiedTime, childFile.lastModified())

        def linkEntry = files[2]
        linkEntry.type == FileInfo.Type.Symlink
        linkEntry.name == childLink.name
        linkEntry.size == 0
        assertTimestampMatches(linkEntry.lastModifiedTime, childLinkAttributes.lastModifiedTime().toMillis())
        // Note java.io.File.lastModified() follows symbolic links, so the following assertions is not verified
        //assertTimestampMatches(linkEntry.lastModifiedTime, childLink.lastModified())

        def missingEntry = files[3]
        missingEntry.type == FileInfo.Type.Symlink
        missingEntry.name == childMissingLink.name
        missingEntry.size == 0
        assertTimestampMatches(missingEntry.lastModifiedTime, childMissingLinkAttributes.lastModifiedTime().toMillis())

        where:
        fileName << names
    }

    @IgnoreIf({ !FilesTest.supportsSymbolicLinks() })
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
        def childDirAttributes = attributes(childDir)
        def childFile = new File(testFile, testFile.name + ".b")
        childFile.text = 'contents'
        def childFileAttributes = attributes(childFile)

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
        dirEntry.type == FileInfo.Type.Directory
        dirEntry.name == childDir.name
        dirEntry.size == 0L
        assertTimestampMatches(dirEntry.lastModifiedTime, childDirAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(dirEntry.lastModifiedTime, childDir.lastModified())

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        assertTimestampMatches(fileEntry.lastModifiedTime, childFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(fileEntry.lastModifiedTime, childFile.lastModified())

        def linkEntry = files[2]
        linkEntry.type == FileInfo.Type.File
        linkEntry.name == childLink.name
        linkEntry.size == fileEntry.size
        assertTimestampMatches(linkEntry.lastModifiedTime, childFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(linkEntry.lastModifiedTime, childFile.lastModified())

        def missingEntry = files[3]
        missingEntry.type == FileInfo.Type.Missing
        missingEntry.name == childMissingLink.name
        missingEntry.size == 0
        missingEntry.lastModifiedTime == 0

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
        assertTimestampMatches(dirEntry.lastModifiedTime, childDirAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(dirEntry.lastModifiedTime, childDir.lastModified())

        def fileEntry = files[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == 8
        assertTimestampMatches(fileEntry.lastModifiedTime, childFileAttributes.lastModifiedTime().toMillis())
        assertTimestampMatches(fileEntry.lastModifiedTime, childFile.lastModified())

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
