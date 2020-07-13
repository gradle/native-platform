
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2020.1"

project {
    params {
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }

    val buildReceiptSource = NativePlatformBuild(Agent.UbuntuAmd64) {
        artifactRules = listOf(artifactRules, buildReceipt).joinToString("\n")
    }
    val testBuilds = listOf(buildReceiptSource) +
        Agent.values().filter { it !in listOf(Agent.UbuntuAmd64, Agent.CentOsAmd64) }.map { NativePlatformBuild(it) }
    testBuilds.forEach(::buildType)
    val compatibilityTestBuilds = listOf(NativePlatformCompatibilityTest(Agent.CentOsAmd64, testBuilds).also(::buildType))
    buildType(BuildTrigger(testBuilds + compatibilityTestBuilds))

    subProject(Publishing(testBuilds, buildReceiptSource))
}
