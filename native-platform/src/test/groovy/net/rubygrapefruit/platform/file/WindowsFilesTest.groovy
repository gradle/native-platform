package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf

@IgnoreIf({ !Platform.current().windows })
class WindowsFilesTest extends FilesTest {
    final WindowsFiles windowsFiles = getIntegration(WindowsFiles)

    @Override
    void assertIsFile(FileInfo stat, File file) {
        assert stat instanceof WindowsFileInfo
        super.assertIsFile(stat, file)
    }

    @Override
    void assertIsDirectory(FileInfo stat, File file) {
        assert stat instanceof WindowsFileInfo
        super.assertIsDirectory(stat, file)
    }

    @Override
    void assertIsSymlink(FileInfo stat, File file) {
        assert stat instanceof WindowsFileInfo
        super.assertIsSymlink(stat, file)
    }

    def "uses same instance for specialized file types"() {
        expect:
        getIntegration(WindowsFiles) == windowsFiles
        getIntegration(Files) == windowsFiles
    }

    def "can stat file using UNC path"() {
        def file = new File(tmpDir, "somefile.txt")
        file.createNewFile()
        def testFile = new File('\\\\localhost\\' + file.absolutePath.charAt(0) + '$\\' + file.absolutePath.substring(2))

        when:
        def stat = windowsFiles.stat(testFile)

        then:
        stat.type == FileInfo.Type.File
    }
}
