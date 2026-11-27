plugins {
    id("com.gradle.develocity").version("4.3.2")
    id("io.github.gradle.develocity-conventions-plugin").version("0.16.0")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
