/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle

class NativePlatformCompatibilityTest(agent: Agent, buildDependencies: List<BuildType>, init: BuildType.() -> Unit = {}) : BuildType({
    name = "Compatibility test on $agent"
    id = RelativeId("CompatibilityTest$agent")

    runOn(agent)

    steps {
        gradle {
            tasks =
                "clean :native-platform:test -PtestVersionFromLocalRepository  ${javaInstallationLocations(agent)}"
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
