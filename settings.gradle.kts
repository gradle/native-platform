plugins {
    id("com.gradle.develocity").version("4.3.1")
    id("io.github.gradle.develocity-conventions-plugin").version("0.12.1")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
