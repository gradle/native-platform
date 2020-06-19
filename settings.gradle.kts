plugins {
    `gradle-enterprise`
}

rootProject.name = "native-platform"

include("testApp")

project(":testApp").projectDir = File(rootDir, "test-app")

enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")

gradleEnterprise {
    buildScan {
        isCaptureTaskInputFiles = true
        if (!System.getenv("CI").isNullOrEmpty()) {
            tag("CI")
            link("TeamCity Build", System.getenv("BUILD_URL"))
            value("BuildId", System.getenv("BUILD_ID"))
            val commitId = System.getenv("BUILD_VCS_NUMBER")
            value("GitCommit", commitId)
            link("Source", "https://github.com/gradle/native-platform/commit/$commitId")
            isUploadInBackground = false
        } else {
            tag("local")
        }
    }
}
