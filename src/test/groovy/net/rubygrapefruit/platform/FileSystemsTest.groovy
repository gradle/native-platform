package net.rubygrapefruit.platform

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class FileSystemsTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final FileSystems fileSystems = Native.get(FileSystems.class)

    def "caches file systems instance"() {
        expect:
        Native.get(FileSystems.class) == fileSystems
    }

    def "can query filesystem details"() {
        expect:
        fileSystems.fileSystems.collect() { it.mountPoint }.containsAll(File.listRoots())
    }
}
