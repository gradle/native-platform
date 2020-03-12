/*
 * Copyright 2020 the original author or authors.
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

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

open class NativePlatformBuild(agent: Agent, init: BuildType.() -> Unit = {}) : BuildType({
    name = "Build ($agent)"
    id = RelativeId("Build$agent")

    runOn(agent)

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build $buildScanInit"
            buildFile = ""
        }
    }

    features {
        publishCommitStatus()
        lowerRequiredFreeDiskSpace()
    }

    failureConditions {
        testFailure = false
        executionTimeoutMin = 5
    }

    artifactRules = """
        build/reports/tests/test/** => test-report
        buildSrc/build/reports/plugin-development/** => buildSrc
        hs_err*
        build/**/output.txt
    """.trimIndent()

    init(this)
})

class BuildTrigger(dependencies: List<BuildType>) : BuildType({
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
        dependencies.forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
    }

    runOn(Agent.Linux)

    features {
        publishCommitStatus()
    }
})
