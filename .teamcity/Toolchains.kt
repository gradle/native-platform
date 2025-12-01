fun javaInstallationLocations(agent: Agent): String {
    val paths = listOf(
        "%env.JDK_21_0%",
        "%${agent.os.osType.lowercase()}.java21.openjdk.${agent.architecture.paramName}%"
    ).joinToString(",")
    return """-Porg.gradle.java.installations.paths=$paths -Dorg.gradle.java.installations.auto-download=false"""
}
