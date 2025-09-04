package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf
import spock.lang.Unroll

import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE

@IgnoreIf({ Platform.current().windows })
class PosixFilesTest extends FilesTest {
    final PosixFiles posixFiles = getIntegration(PosixFiles)

    @Override
    void assertIsFile(FileInfo stat, File file) {
        assert stat instanceof PosixFileInfo
        super.assertIsFile(stat, file)
        stat.mode == mode(attributes(file))
        assert stat.uid != 0
        assert stat.gid >= 0
        assert stat.blockSize
    }

    @Override
    void assertIsDirectory(FileInfo stat, File file) {
        assert stat instanceof PosixFileInfo
        super.assertIsDirectory(stat, file)
        stat.mode == mode(attributes(file))
        assert stat.uid != 0
        assert stat.gid >= 0
        assert stat.blockSize
    }

    @Override
    void assertIsSymlink(FileInfo stat, File file) {
        assert stat instanceof PosixFileInfo
        super.assertIsSymlink(stat, file)
        stat.mode == mode(attributes(file))
        assert stat.uid != 0
        assert stat.gid >= 0
        assert stat.blockSize
    }

    @Override
    void assertIsMissing(FileInfo stat) {
        assert stat instanceof PosixFileInfo
        super.assertIsMissing(stat)
        assert stat.uid == 0
        assert stat.gid == 0
        assert stat.blockSize == 0
    }

    def "uses same instance for specialized file types"() {
        expect:
        getIntegration(PosixFiles) == posixFiles
        getIntegration(Files) == posixFiles
    }

    def "can stat a file with no read permissions"() {
        def testFile = new File(tmpDir, "test.file")
        testFile.createNewFile()
        chmod(testFile, [OWNER_WRITE])

        when:
        def stat = posixFiles.stat(testFile)

        then:
        stat.type == FileInfo.Type.File

        cleanup:
        chmod(testFile, [OWNER_READ, OWNER_WRITE])
    }

    def "cannot stat a file with no execute permission on parent"() {
        def testDir = new File(tmpDir, "test-dir")
        testDir.mkdirs()
        def testFile = new File(testDir, "test.file")
        chmod(testDir, permissions)

        when:
        posixFiles.stat(testFile)

        then:
        def e = thrown(FilePermissionException)
        e.message == "Could not get file details of $testFile: permission denied"

        cleanup:
        chmod(testDir, [OWNER_READ, OWNER_WRITE])

        where:
        permissions   | _
        []            | _
        [OWNER_WRITE] | _
        [OWNER_READ]  | _
    }

    def "can stat a symlink with no read permissions on symlink"() {
        def testDir = new File(tmpDir, "test-dir")
        testDir.mkdirs()
        new File(testDir, "test.file").createNewFile()
        def linkFile = new File(testDir, "link")
        posixFiles.symlink(linkFile, "test.file")
        chmod(linkFile, [])

        when:
        def stat = posixFiles.stat(linkFile, true)

        then:
        stat.type == FileInfo.Type.File

        when:
        stat = posixFiles.stat(linkFile, false)

        then:
        stat.type == FileInfo.Type.Symlink

        cleanup:
        chmod(linkFile, [OWNER_READ, OWNER_WRITE])
    }

    def "cannot stat a symlink with no read permissions on parent of target"() {
        def dir = new File(tmpDir, "first-test-dir")
        dir.mkdirs()
        def testDir = new File(dir, "test-dir")
        testDir.mkdirs()
        def testFile = new File(testDir, "test.file")
        testFile.createNewFile()
        chmod(testDir, [OWNER_WRITE])

        def linkFile = new File(dir, "link")
        linkFile.delete()
        posixFiles.symlink(linkFile, "test-dir/test.file")

        when:
        posixFiles.stat(linkFile, true)

        then:
        def e = thrown(FilePermissionException)
        e.message == "Could not get file details of $linkFile: permission denied"

        cleanup:
        chmod(testDir, [OWNER_READ, OWNER_WRITE])
    }

    @Unroll
    def "stat follows symlinks to parent directory"() {
        def parentDir = new File(tmpDir, "first-test-dir")
        parentDir.mkdirs()
        def testFile = new File(parentDir, fileName)
        testFile.parentFile.mkdirs()
        testFile.text = "content"
        def dir = new File(tmpDir, "first-test-dir")
        dir.mkdirs()
        def link = new File(dir, "link")

        given:
        posixFiles.symlink(link, parentDir.absolutePath)

        when:
        def stat = posixFiles.stat(new File(link, fileName))

        then:
        assertIsFile(stat, testFile)

        where:
        fileName << names
    }

    def "directory listing follows symlinks to dir"() {
        def dir = new File(tmpDir, "first-test-dir")
        dir.mkdirs()
        new File(dir, "a").text = 'content'
        new File(dir, "b").text = 'content'
        def testDir = new File(tmpDir, "second-test-dir")
        testDir.mkdirs()
        def link1 = new File(testDir, "some-dir")
        def link2 = new File(testDir, "link2")
        posixFiles.symlink(link1, "link2")
        posixFiles.symlink(link2, dir.absolutePath)

        expect:
        def list = posixFiles.listDir(link1)
        list.size() == 2
        list*.name.sort() == ["a", "b"]
    }

    def "cannot list directory without read and execute permissions"() {
        def dir = new File(tmpDir, "first-test-dir")
        dir.mkdirs()
        new File(dir, "a").text = 'content'
        new File(dir, "b").text = 'content'
        chmod(dir, permissions)

        when:
        posixFiles.listDir(dir)

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

    @Unroll
    def "can set mode on a file"() {
        def testFile = new File(tmpDir, fileName)
        testFile.createNewFile()

        when:
        posixFiles.setMode(testFile, fileMode)

        then:
        mode(attributes(testFile)) == fileMode
        posixFiles.getMode(testFile) == fileMode
        posixFiles.stat(testFile).mode == fileMode

        where:
        fileName << maybeWithUnicde(["test.txt", "test\u03b1\u2295.txt", "test2.txt"])
        fileMode << [0777, 0740, 0644]
    }

    @Unroll
    def "can set mode on a directory"() {
        def testFile = new File(tmpDir, fileName)
        testFile.mkdirs()

        when:
        posixFiles.setMode(testFile, fileMode)

        then:
        mode(attributes(testFile)) == fileMode
        posixFiles.getMode(testFile) == fileMode
        posixFiles.stat(testFile).mode == fileMode

        where:
        fileName << maybeWithUnicde(["test-dir", "test\u03b1\u2295-dir", "test2.txt"])
        fileMode << [0777, 0740, 0644]
    }

    def "cannot set mode on file that does not exist"() {
        def testFile = new File(tmpDir, "unknown")

        when:
        posixFiles.setMode(testFile, 0660)

        then:
        NativeException e = thrown()
        e.message == "Could not set UNIX mode on $testFile: could not chmod file (errno 2: No such file or directory)"
    }

    def "cannot get mode on file that does not exist"() {
        def testFile = new File(tmpDir, "unknown")

        when:
        posixFiles.getMode(testFile)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $testFile: file does not exist."
    }

    @Unroll
    def "can create symbolic link"() {
        def testFile = new File(tmpDir, name)
        testFile.parentFile.mkdirs()
        testFile.text = "hi"
        def symlinkFile = new File(tmpDir, name + ".link")

        when:
        posixFiles.symlink(symlinkFile, testFile.name)

        then:
        symlinkFile.file
        symlinkFile.text == "hi"
        symlinkFile.canonicalFile == testFile.canonicalFile

        where:
        name << names
    }

    @Unroll
    def "can read symbolic link"() {
        def symlinkFile = new File(tmpDir, name)
        symlinkFile.parentFile.mkdirs()

        when:
        posixFiles.symlink(symlinkFile, name)

        then:
        posixFiles.readLink(symlinkFile) == name

        where:
        name << names
    }

    def "cannot read a symlink that does not exist"() {
        def symlinkFile = new File(tmpDir, "symlink")

        when:
        posixFiles.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not lstat file (errno 2: No such file or directory)"
    }

    def "cannot read a symlink that is not a symlink"() {
        def symlinkFile = new File(tmpDir, "not-a-symlink.txt")
        symlinkFile.createNewFile()

        when:
        posixFiles.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not readlink (errno 22: Invalid argument)"
    }

    @Unroll
    def "can get mode for a file behind symbolic link"() {
        def testFile = new File(tmpDir, name)
        testFile.parentFile.mkdirs()
        testFile.text = "hi"
        posixFiles.setMode(testFile, 0660)

        def symlinkFile = new File(tmpDir, name + '.link')

        when:
        posixFiles.symlink(symlinkFile, testFile.name)

        then:
        posixFiles.getMode(symlinkFile, true) == posixFiles.getMode(testFile)
        posixFiles.getMode(symlinkFile, false) != posixFiles.getMode(testFile)
        posixFiles.getMode(symlinkFile, false) == posixFiles.getMode(symlinkFile)

        posixFiles.getMode(testFile, true) == posixFiles.getMode(testFile)

        where:
        name << names
    }

    @Unroll
    def "can get mode for a file behind broken symbolic link"() {
        def brokenSymlinkFile = new File(tmpDir, name + '.link')
        brokenSymlinkFile.parentFile.mkdirs()
        posixFiles.symlink(brokenSymlinkFile, name)

        when:
        posixFiles.getMode(brokenSymlinkFile, false)
        posixFiles.getMode(brokenSymlinkFile)

        then:
        noExceptionThrown()

        when:
        posixFiles.getMode(brokenSymlinkFile, true)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $name: file does not exist."

        where:
        name << names
    }

    @Override
    PosixFileAttributes attributes(File file) {
        return java.nio.file.Files.getFileAttributeView(file.toPath(), PosixFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes()
    }

    void chmod(File file, Collection<PosixFilePermission> perms) {
        PosixFileAttributeView fileAttributeView = java.nio.file.Files.getFileAttributeView(file.toPath(), PosixFileAttributeView)
        fileAttributeView.setPermissions(perms as Set)
    }
}
