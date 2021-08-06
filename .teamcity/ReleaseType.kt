enum class ReleaseType(val gradleProperty: String, val username: String, val password: String, val userProvidedVersion: Boolean = false) {
    Snapshot("snapshot", "bot-build-tool", "credentialsJSON:fc942743-b732-4204-a131-26bb066c2073"),
    Alpha("alpha", "bot-build-tool", "credentialsJSON:fc942743-b732-4204-a131-26bb066c2073", true),
    Milestone("milestone", "bot-build-tool", "credentialsJSON:fc942743-b732-4204-a131-26bb066c2073"),
    Release("release", "bot-build-tool", "credentialsJSON:fc942743-b732-4204-a131-26bb066c2073")
}
