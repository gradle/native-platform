plugins {
    `gradle-enterprise`
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.4")
}

rootProject.name = "native-platform"

include("test-app")
include("native-platform")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
