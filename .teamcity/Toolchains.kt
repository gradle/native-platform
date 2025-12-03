fun javaInstallationLocations(): String {
    return """-Dorg.gradle.java.installations.fromEnv=JDK8,JDK21 -Dorg.gradle.java.installations.auto-download=false"""
}
