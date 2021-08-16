const val gradleInternalRepositoryUsername = "%gradle.internal.repository.build-tool.publish.username%"
const val gradleInternalRepositoryPassword = "%gradle.internal.repository.build-tool.publish.password%"

enum class ReleaseType(val gradleProperty: String, val username: String, val password: String, val userProvidedVersion: Boolean = false) {
    Snapshot("snapshot", gradleInternalRepositoryUsername, gradleInternalRepositoryPassword),
    Alpha("alpha", gradleInternalRepositoryUsername, gradleInternalRepositoryPassword, true),
    Milestone("milestone", gradleInternalRepositoryUsername, gradleInternalRepositoryPassword),
    Release("release", gradleInternalRepositoryUsername, gradleInternalRepositoryPassword)
}
