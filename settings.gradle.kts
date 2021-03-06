plugins {
    `gradle-enterprise`
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.2")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")
include("file-events")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
