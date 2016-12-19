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

import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

import static java.nio.file.attribute.PosixFilePermission.*

@IgnoreIf({ Platform.current().windows })
class PosixFilesTest extends AbstractFilesTest {
    @Rule
    TemporaryFolder tmpDir
    final PosixFiles files = Native.get(PosixFiles.class)

    def "caches file instance"() {
        expect:
        Native.get(PosixFiles.class) == files
        Native.get(Files.class) == files
    }

    def "can stat a file"() {
        def testFile = tmpDir.newFile(fileName)
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
        stat.mode == mode(attributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == testFile.size()
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()
        stat.blockSize

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can stat a directory"() {
        def testFile = tmpDir.newFolder(fileName)
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Directory
        stat.mode == mode(attributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()
        stat.blockSize

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "can stat a missing file"() {
        def testFile = new File(tmpDir.root, fileName)

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.Missing
        stat.mode == 0
        stat.uid == 0
        stat.gid == 0
        stat.size == 0
        stat.lastModifiedTime == 0
        stat.blockSize == 0

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "can stat a file with no read permissions"() {
        def testFile = tmpDir.newFile("test.file")
        chmod(testFile, [OWNER_WRITE])

        when:
        def stat = files.stat(testFile)

        then:
        stat.type == FileInfo.Type.File

        cleanup:
        chmod(testFile, [OWNER_READ, OWNER_WRITE])
    }

    def "cannot stat a file with no execute permission on parent"() {
        def testDir = tmpDir.newFolder("test-dir")
        def testFile = new File(testDir, "test.file")
        chmod(testDir, permissions)

        when:
        files.stat(testFile)

        then:
        def e = thrown(FilePermissionException)
        e.message == "Could not get file details of $testFile: permission denied"

        cleanup:
        chmod(testDir, [OWNER_READ, OWNER_WRITE])

        where:
        permissions     | _
        []              | _
        [OWNER_WRITE]   | _
        [OWNER_READ]    | _
    }

    def "can stat a symlink that references a directory"() {
        def linkFile = new File(tmpDir.newFolder("parent"), fileName)
        def dir = new File(linkFile.parentFile, "target")
        dir.mkdirs()
        dir.lastModified = dir.lastModified() - 2000

        given:
        files.symlink(linkFile, "target")
        def linkAttributes = attributes(linkFile)
        def dirAttributes = attributes(dir)

        when:
        def stat = files.stat(linkFile)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(linkAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == linkAttributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, false)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(linkAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == linkAttributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, true)

        then:
        stat.type == FileInfo.Type.Directory
        stat.mode == mode(dirAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == dirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == dir.lastModified()
        stat.blockSize

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can stat a symlink that references a file"() {
        def linkFile = new File(tmpDir.newFolder("parent"), fileName)
        def file = new File(linkFile.parentFile, "target")
        file.createNewFile()
        file.lastModified = file.lastModified() - 2000

        given:
        files.symlink(linkFile, "target")
        def linkAttributes = attributes(linkFile)
        def fileAttributes = attributes(file)

        when:
        def stat = files.stat(linkFile)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(linkAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == linkAttributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, false)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(linkAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == linkAttributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, true)

        then:
        stat.type == FileInfo.Type.File
        stat.mode == mode(fileAttributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == file.length()
        stat.lastModifiedTime == fileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == file.lastModified()
        stat.blockSize

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can stat a symlink that references a missing file"() {
        def linkFile = new File(tmpDir.newFolder("parent"), fileName)

        given:
        files.symlink(linkFile, "missing/" + fileName)
        def attributes = attributes(linkFile)

        when:
        def stat = files.stat(linkFile)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(attributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, false)

        then:
        stat.type == FileInfo.Type.Symlink
        stat.mode == mode(attributes)
        stat.uid != 0
        stat.gid >= 0
        stat.size == 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        stat.blockSize

        when:
        stat = files.stat(linkFile, true)

        then:
        stat.type == FileInfo.Type.Missing
        stat.mode == 0
        stat.uid == 0
        stat.gid == 0
        stat.size == 0
        stat.lastModifiedTime == 0
        stat.blockSize == 0

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can stat a symlink with no read permissions on symlink"() {
        def testDir = tmpDir.newFolder("test-dir")
        new File(testDir, "test.file").createNewFile()
        def linkFile = new File(testDir, "link")
        files.symlink(linkFile, "test.file")
        chmod(linkFile, [])

        when:
        def stat = files.stat(linkFile, true)

        then:
        stat.type == FileInfo.Type.File

        when:
        stat = files.stat(linkFile, false)

        then:
        stat.type == FileInfo.Type.Symlink

        cleanup:
        chmod(linkFile, [OWNER_READ, OWNER_WRITE])
    }

    def "cannot stat a symlink with no read permissions on parent of target"() {
        def dir = tmpDir.newFolder()
        def testDir = new File(dir, "test-dir")
        testDir.mkdirs()
        def testFile = new File(testDir, "test.file")
        testFile.createNewFile()
        chmod(testDir, [OWNER_WRITE])

        def linkFile = new File(dir, "link")
        linkFile.delete()
        files.symlink(linkFile, "test-dir/test.file")

        when:
        files.stat(linkFile, true)

        then:
        def e = thrown(FilePermissionException)
        e.message == "Could not get file details of $linkFile: permission denied"

        cleanup:
        chmod(testDir, [OWNER_READ, OWNER_WRITE])
    }

    def "stat follows symlinks to parent directory"() {
        def parentDir = tmpDir.newFolder()
        def testFile = new File(parentDir, fileName)
        testFile.text = "content"
        def link = new File(tmpDir.newFolder(), "link")

        given:
        files.symlink(link, parentDir.absolutePath)
        def attributes = attributes(testFile)

        when:
        def stat = files.stat(new File(link, fileName))

        then:
        stat.type == FileInfo.Type.File
        stat.mode == mode(attributes)
        stat.uid != 0
        stat.gid >= 0
        stat.lastModifiedTime == attributes.lastModifiedTime().toMillis()
        toJavaFileTime(stat.lastModifiedTime) == testFile.lastModified()
        stat.blockSize

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt"]
    }

    def "can list contents of a directory containing symlinks"() {
        def dir = tmpDir.newFolder(fileName)
        def linkTarget = tmpDir.newFile()
        linkTarget.text = "some content"
        linkTarget.lastModified = linkTarget.lastModified() - 2000
        def linkTargetAttributes = attributes(linkTarget)

        def childDir = new File(dir, fileName + ".a")
        childDir.mkdirs()
        def childDirAttributes = attributes(childDir)
        def childFile = new File(dir, fileName + ".b")
        childFile.text = 'content'
        def childFileAttributes = attributes(childFile)
        def childLink = new File(dir, fileName + ".c")
        files.symlink(childLink, "../" + linkTarget.name)
        def childLinkAttributes = attributes(childLink)

        when:
        def entries = files.listDir(dir)

        then:
        entries.size() == 3
        entries.sort { it.name }

        def dirEntry = entries[0]
        dirEntry.type == FileInfo.Type.Directory
        dirEntry.name == childDir.name
        dirEntry.size == 0
        dirEntry.lastModifiedTime == childDirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(dirEntry.lastModifiedTime) == childDir.lastModified()

        def fileEntry = entries[1]
        fileEntry.type == FileInfo.Type.File
        fileEntry.name == childFile.name
        fileEntry.size == childFile.length()
        fileEntry.lastModifiedTime == childFileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(fileEntry.lastModifiedTime) == childFile.lastModified()

        def linkEntry = entries[2]
        linkEntry.type == FileInfo.Type.Symlink
        linkEntry.name == childLink.name
        linkEntry.size == 0
        linkEntry.lastModifiedTime == childLinkAttributes.lastModifiedTime().toMillis()

        when:
        entries = files.listDir(dir, false)

        then:
        entries.size() == 3
        entries.sort { it.name }

        def dirEntry2 = entries[0]
        dirEntry2.type == FileInfo.Type.Directory
        dirEntry2.name == childDir.name
        dirEntry2.size == 0
        dirEntry2.lastModifiedTime == childDirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(dirEntry2.lastModifiedTime) == childDir.lastModified()

        def fileEntry2 = entries[1]
        fileEntry2.type == FileInfo.Type.File
        fileEntry2.name == childFile.name
        fileEntry2.size == childFile.length()
        fileEntry2.lastModifiedTime == childFileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(fileEntry2.lastModifiedTime) == childFile.lastModified()

        def linkEntry2 = entries[2]
        linkEntry2.type == FileInfo.Type.Symlink
        linkEntry2.name == childLink.name
        linkEntry2.size == 0
        linkEntry2.lastModifiedTime == childLinkAttributes.lastModifiedTime().toMillis()

        when:
        entries = files.listDir(dir, true)

        then:
        entries.size() == 3
        entries.sort { it.name }

        def dirEntry3 = entries[0]
        dirEntry3.type == FileInfo.Type.Directory
        dirEntry3.name == childDir.name
        dirEntry3.size == 0
        dirEntry3.lastModifiedTime == childDirAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(dirEntry3.lastModifiedTime) == childDir.lastModified()

        def fileEntry3 = entries[1]
        fileEntry3.type == FileInfo.Type.File
        fileEntry3.name == childFile.name
        fileEntry3.size == childFile.length()
        fileEntry3.lastModifiedTime == childFileAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(fileEntry3.lastModifiedTime) == childFile.lastModified()

        def linkEntry3 = entries[2]
        linkEntry3.type == FileInfo.Type.File
        linkEntry3.name == childLink.name
        linkEntry3.size == linkTarget.length()
        linkEntry3.lastModifiedTime == linkTargetAttributes.lastModifiedTime().toMillis()
        toJavaFileTime(linkEntry3.lastModifiedTime) == linkTarget.lastModified()

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir"]
    }

    def "directory listing follows symlinks to dir"() {
        def dir = tmpDir.newFolder()
        new File(dir, "a").text = 'content'
        new File(dir, "b").text = 'content'
        def testDir = tmpDir.newFolder()
        def link1 = new File(testDir, "some-dir")
        def link2 = new File(testDir, "link2")
        files.symlink(link1, "link2")
        files.symlink(link2, dir.absolutePath)

        expect:
        def list = files.listDir(link1)
        list.size() == 2
        list*.name.sort() == ["a", "b"]
    }

    def "cannot list directory without read and execute permissions"() {
        def dir = tmpDir.newFolder()
        new File(dir, "a").text = 'content'
        new File(dir, "b").text = 'content'
        chmod(dir, permissions)

        when:
        files.listDir(dir)

        then:
        def e = thrown(FilePermissionException)
        e.message == "Could not list directory $dir: permission denied"

        cleanup:
        chmod(dir, [OWNER_READ, OWNER_WRITE, OWNER_EXECUTE])

        where:
        permissions     | _
        []              | _
        [OWNER_WRITE]   | _
        [OWNER_EXECUTE] | _
        [OWNER_READ]    | _
    }

    def "can set mode on a file"() {
        def testFile = tmpDir.newFile(fileName)

        when:
        files.setMode(testFile, fileMode)

        then:
        mode(attributes(testFile)) == fileMode
        files.getMode(testFile) == fileMode
        files.stat(testFile).mode == fileMode

        where:
        fileName << ["test.txt", "test\u03b1\u2295.txt", "test2.txt"]
        fileMode << [0777, 0740, 0644]
    }

    def "can set mode on a directory"() {
        def testFile = tmpDir.newFolder(fileName)

        when:
        files.setMode(testFile, fileMode)

        then:
        mode(attributes(testFile)) == fileMode
        files.getMode(testFile) == fileMode
        files.stat(testFile).mode == fileMode

        where:
        fileName << ["test-dir", "test\u03b1\u2295-dir", "test2.txt"]
        fileMode << [0777, 0740, 0644]
    }

    def "cannot set mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        files.setMode(testFile, 0660)

        then:
        NativeException e = thrown()
        e.message == "Could not set UNIX mode on $testFile: could not chmod file (errno 2: No such file or directory)"
    }

    def "cannot get mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        files.getMode(testFile)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $testFile: file does not exist."
    }

    def "can create symbolic link"() {
        def testFile = new File(tmpDir.root, "test.txt")
        testFile.text = "hi"
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.symlink(symlinkFile, testFile.name)

        then:
        symlinkFile.file
        symlinkFile.text == "hi"
        symlinkFile.canonicalFile == testFile.canonicalFile
    }

    def "can read symbolic link"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.symlink(symlinkFile, "target")

        then:
        files.readLink(symlinkFile) == "target"
    }

    def "cannot read a symlink that does not exist"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        files.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not lstat file (errno 2: No such file or directory)"
    }

    def "cannot read a symlink that is not a symlink"() {
        def symlinkFile = tmpDir.newFile("not-a-symlink.txt")

        when:
        files.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not readlink (errno 22: Invalid argument)"
    }

    def "can create and read symlink with unicode in its name"() {
        def testFile = new File(tmpDir.root, "target\u03b2\u2295")
        testFile.text = 'hi'
        def symlinkFile = new File(tmpDir.root, "symlink\u03b2\u2296")

        when:
        files.symlink(symlinkFile, testFile.name)

        then:
        files.readLink(symlinkFile) == testFile.name
        symlinkFile.file
        symlinkFile.canonicalFile == testFile.canonicalFile
    }

    int mode(PosixFileAttributes attributes) {
        int mode = 0
        [OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE].each {
            mode = mode << 1
            if (attributes.permissions().contains(it)) {
                mode |= 1
            }
        }
        return mode
    }

    void chmod(File file, Collection<PosixFilePermission> perms) {
        PosixFileAttributeView fileAttributeView = java.nio.file.Files.getFileAttributeView(file.toPath(), PosixFileAttributeView)
        fileAttributeView.setPermissions(perms as Set)
    }

    PosixFileAttributes attributes(File file) {
        return java.nio.file.Files.getFileAttributeView(file.toPath(), PosixFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes()
    }
}
