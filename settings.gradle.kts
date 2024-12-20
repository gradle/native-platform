plugins {
    id("com.gradle.develocity").version("3.18.1")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.2")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
