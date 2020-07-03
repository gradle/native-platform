plugins {
    `gradle-enterprise`
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.3")
}

rootProject.name = "native-platform"

include("testApp")

project(":testApp").projectDir = File(rootDir, "test-app")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")
