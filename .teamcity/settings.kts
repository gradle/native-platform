
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot
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

version = "2019.2"

project {

    vcsRoot(HttpsGithubComWolfsNativePlatformGitRefsHeadsMaster)

    buildType(BuildMacOS)
    buildType(BuildTrigger)
    buildType(BuildLinux)
    buildType(BuildWindows)
    buildTypesOrder = arrayListOf(BuildTrigger, BuildLinux, BuildMacOS, BuildWindows)

    subProject(Publishing)
}

open class NativePlatformBuild(init: BuildType.() -> Unit) : BuildType({
    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build -I gradle/init-scripts/build-scan.init.gradle.kts"
            buildFile = ""
        }
    }

    init(this)
})

object BuildWindows : NativePlatformBuild({
    name = "Build (Windows)"
    runOn(Os.Windows)
})

object BuildLinux : NativePlatformBuild({
    name = "Build (Linux)"

    artifactRules = "build-receipt.properties"
    runOn(Os.Linux)
})

object BuildMacOS : NativePlatformBuild({
    name = "Build (macOS)"
    runOn(Os.MacOs)
})

object BuildTrigger : BuildType({
    name = "Build (Trigger)"

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
            branchFilter = """
                +:*
                -:pull/*
            """.trimIndent()
        }
    }

    dependencies {
        listOf(BuildWindows, BuildLinux, BuildMacOS).forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }

    runOn(Os.Linux)
})

object HttpsGithubComWolfsNativePlatformGitRefsHeadsMaster : GitVcsRoot({
    name = "https://github.com/wolfs/native-platform.git#refs/heads/master"
    url = "https://github.com/wolfs/native-platform.git"
    branchSpec = "+:refs/heads/*"
})


object Publishing : Project({
    name = "Publishing"

    buildType(Publishing_PublishMacOsSnapshot)
    buildType(Publishing_PublishJavaApiSnapshot)
    buildType(Publishing_PublishWindowsSnapshot)
    buildType(Publishing_PublishLinuxSnapshot)
    buildTypesOrder = arrayListOf(Publishing_PublishJavaApiSnapshot, Publishing_PublishLinuxSnapshot, Publishing_PublishMacOsSnapshot, Publishing_PublishWindowsSnapshot)
})

open class NativePlatformPublishSnapshot(uploadTasks: List<String>, init: BuildType.() -> Unit) : BuildType({
    params {
        param("ARTIFACTORY_USERNAME", "bot-build-tool")
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    steps {
        uploadTasks.forEach { task ->
            gradle {
                name = "Gradle $task"
                tasks = "clean $task -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
                buildFile = ""
            }
        }
    }

    dependencies {
        listOf(BuildLinux, BuildWindows, BuildMacOS).forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(BuildLinux) {
            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions/"
            }
        }
    }

    init(this)
})

object Publishing_PublishJavaApiSnapshot : NativePlatformPublishSnapshot(listOf(":uploadMain", ":testApp:uploadMain"), {
    name = "Publish Native Platform snapshot"
    runOn(Os.Linux)

    dependencies {
        listOf(Publishing_PublishLinuxSnapshot, Publishing_PublishMacOsSnapshot, Publishing_PublishWindowsSnapshot).forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }
})

object Publishing_PublishLinuxSnapshot : NativePlatformPublishSnapshot(listOf(":uploadJni"), {
    name = "Publish Linux snapshot"
    runOn(Os.Linux)
})

object Publishing_PublishMacOsSnapshot : NativePlatformPublishSnapshot(listOf(":uploadJni"), {
    name = "Publish MacOs snapshot"
    runOn(Os.MacOs)
})

object Publishing_PublishWindowsSnapshot : NativePlatformPublishSnapshot(listOf(":uploadJni"), {
    name = "Publish Windows snapshot"
    runOn(Os.Windows)
})
