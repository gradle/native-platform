package net.rubygrapefruit.platform

import spock.lang.Specification

class NativePlatformSpec extends Specification {
    static boolean nativeInitialized

    def setupSpec() {
        if (!nativeInitialized) {
            def tempRoot = java.nio.file.Paths.get("build/test-outputs")
            java.nio.file.Files.createDirectories(tempRoot)
            def cacheDir = java.nio.file.Files.createTempDirectory(tempRoot, "native-platform").toFile()
            cacheDir.mkdirs()
            Native.init(cacheDir)
            nativeInitialized = true
        }
    }
}
