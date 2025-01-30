package net.rubygrapefruit.platform

import spock.lang.Specification

class NativePlatformSpec extends Specification {
    private static Native nativeIntegration

    static protected <T> T getIntegration(Class<T> type) {
        synchronized (NativePlatformSpec) {
            if (nativeIntegration == null) {
                def tempRoot = java.nio.file.Paths.get("build/test-outputs")
                java.nio.file.Files.createDirectories(tempRoot)
                def cacheDir = java.nio.file.Files.createTempDirectory(tempRoot, "native-platform").toFile()
                cacheDir.mkdirs()
                nativeIntegration = Native.init(cacheDir)
            }
        }
        nativeIntegration.get(type)
    }
}
