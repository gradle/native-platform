import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

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
    buildType(Build)
    buildTypesOrder = arrayListOf(BuildTrigger, BuildLinux, BuildMacOS, Build)

    subProject(Publishing)
}

object Build : BuildType({
    name = "Build (Windows)"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build -I gradle/init-scripts/build-scan.init.gradle.kts"
            buildFile = ""
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})

object BuildLinux : BuildType({
    name = "Build (Linux)"

    artifactRules = "build-receipt.properties"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build -I gradle/init-scripts/build-scan.init.gradle.kts"
            buildFile = ""
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object BuildMacOS : BuildType({
    name = "Build (macOS)"

    params {
        param("env.JAVA_HOME", "%macos.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build -I gradle/init-scripts/build-scan.init.gradle.kts"
            buildFile = ""
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac OS X")
    }
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
        snapshot(Build) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(BuildLinux) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(BuildMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
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

object Publishing_PublishJavaApiSnapshot : BuildType({
    name = "Publish Native Platform snapshot"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("ARTIFACTORY_USERNAME", "bot-build-tool")
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)

        cleanCheckout = true
    }

    steps {
        gradle {
            tasks = "clean :uploadMain -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
            buildFile = ""
        }
        gradle {
            name = "New build step"
            tasks = ":testApp:uploadMain -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
            buildFile = ""
        }
    }

    dependencies {
        snapshot(Build) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        dependency(BuildLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions/"
            }
        }
        snapshot(BuildMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(Publishing_PublishLinuxSnapshot) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(Publishing_PublishMacOsSnapshot) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(Publishing_PublishWindowsSnapshot) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object Publishing_PublishLinuxSnapshot : BuildType({
    name = "Publish Linux snapshot"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("ARTIFACTORY_USERNAME", "bot-build-tool")
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)

        cleanCheckout = true
    }

    steps {
        gradle {
            tasks = "clean :uploadJni -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
            buildFile = ""
        }
    }

    dependencies {
        snapshot(Build) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        dependency(BuildLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions/"
            }
        }
        snapshot(BuildMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object Publishing_PublishMacOsSnapshot : BuildType({
    name = "Publish MacOs snapshot"

    params {
        param("env.JAVA_HOME", "%macos.java8.oracle.64bit%")
        param("ARTIFACTORY_USERNAME", "bot-build-tool")
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean :uploadJni -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
            buildFile = ""
        }
    }

    dependencies {
        snapshot(Build) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        dependency(BuildLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions/"
            }
        }
        snapshot(BuildMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac")
    }
})

object Publishing_PublishWindowsSnapshot : BuildType({
    name = "Publish Windows snapshot"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
        param("ARTIFACTORY_USERNAME", "bot-build-tool")
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:d94612fb-3291-41f5-b043-e2b3994aeeb4", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean :uploadJni -I gradle/init-scripts/build-scan.init.gradle.kts -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
            buildFile = ""
        }
    }

    dependencies {
        snapshot(Build) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        dependency(BuildLinux) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions/"
            }
        }
        snapshot(BuildMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})
