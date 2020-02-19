enum class ReleaseType(val gradleProperty: String, val username: String, val password: String, val userProvidedVersion: Boolean = false) {
    Snapshot("snapshot", "bot-build-tool", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4"),
    Alpha("alpha", "bot-build-tool", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", true),
    Milestone("milestone", "adammurdoch", "credentialsJSON:762b676d-f41d-434b-979f-6d3d76694033"),
    Release("release", "adammurdoch", "credentialsJSON:762b676d-f41d-434b-979f-6d3d76694033")
}
