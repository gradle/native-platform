import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

class NativePlatformCompatibilityTest(agent: Agent, buildDependencies: List<BuildType>, init: BuildType.() -> Unit = {}) : BuildType({
    name = "Compatibility test on $agent"
    id = RelativeId("CompatibilityTest$agent")

    runOn(agent)

    steps {
        gradle {
            tasks = "clean :native-platform:test :file-events:test -PtestVersionFromLocalRepository"
            buildFile = ""
        }
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    features {
        publishCommitStatus()
        lowerRequiredFreeDiskSpace()
    }

    failureConditions {
        testFailure = false
        executionTimeoutMin = 15
    }

    artifactRules = """
        hs_err*
        **/build/**/output.txt
    """.trimIndent() + "\n$archiveReports"

    dependencies {
        buildDependencies.forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
            dependency(it) {
                artifacts {
                    cleanDestination = true
                    artifactRules = "repo => incoming-repo/"
                }
            }
        }
    }

    init(this)
})
