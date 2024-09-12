plugins {
    id("com.gradle.develocity").version("3.17.5")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.1")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
