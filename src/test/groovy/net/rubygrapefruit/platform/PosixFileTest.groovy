package net.rubygrapefruit.platform

import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import net.rubygrapefruit.platform.internal.Platform

@IgnoreIf({Platform.current().windows})
class PosixFileTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final PosixFile file = Native.get(PosixFile.class)

    def "can set mode on a file"() {
        def testFile = tmpDir.newFile("test.txt")

        when:
        file.setMode(testFile, 0740)

        then:
        file.getMode(testFile) == 0740
    }

    def "can set mode on a file with unicode in its name"() {
        def testFile = tmpDir.newFile("test\u03b1.txt")

        when:
        file.setMode(testFile, 0740)

        then:
        file.getMode(testFile) == 0740
    }

    def "cannot set mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        file.setMode(testFile, 0660)

        then:
        NativeException e = thrown()
        e.message == "Could not set UNIX mode on $testFile: could not chmod file (errno 2)"
    }

    def "cannot get mode on file that does not exist"() {
        def testFile = new File(tmpDir.root, "unknown")

        when:
        file.getMode(testFile)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $testFile: could not stat file (errno 2)"
    }

    def "can create symbolic link"() {
        def testFile = new File(tmpDir.root, "test.txt")
        testFile.text = "hi"
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        file.symlink(symlinkFile, testFile.name)

        then:
        symlinkFile.file
        symlinkFile.text == "hi"
        symlinkFile.canonicalFile == testFile.canonicalFile
    }

    def "can read symbolic link"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        file.symlink(symlinkFile, "target")

        then:
        file.readLink(symlinkFile) == "target"
    }

    def "cannot read a symlink that does not exist"() {
        def symlinkFile = new File(tmpDir.root, "symlink")

        when:
        file.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not lstat file (errno 2)"
    }

    def "cannot read a symlink that is not a symlink"() {
        def symlinkFile = tmpDir.newFile("not-a-symlink.txt")

        when:
        file.readLink(symlinkFile)

        then:
        NativeException e = thrown()
        e.message == "Could not read symlink $symlinkFile: could not readlink (errno 22)"
    }

    def "can create and read symlink with unicode in its name"() {
        def testFile = new File(tmpDir.root, "target\u03b2")
        testFile.text = 'hi'
        def symlinkFile = new File(tmpDir.root, "symlink\u03b2")

        when:
        file.symlink(symlinkFile, testFile.name)

        then:
        file.readLink(symlinkFile) == testFile.name
        symlinkFile.file
        symlinkFile.canonicalFile == testFile.canonicalFile
    }
}
