package net.rubygrapefruit.platform

import spock.lang.Specification
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class UnixFileModeTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final UnixFileMode file = Platform.get(UnixFileMode.class)

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

    def "throws exception on failure to set mode"() {
        def file = new File(tmpDir.root, "unknown")

        when:
        this.file.setMode(file, 0660)

        then:
        NativeException e = thrown()
        e.message == "Could not set UNIX mode on $file. Errno is 2."
    }

    def "throws exception on failure to get mode"() {
        def file = new File(tmpDir.root, "unknown")

        when:
        this.file.getMode(file)

        then:
        NativeException e = thrown()
        e.message == "Could not get UNIX mode on $file. Errno is 2."
    }
}
