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

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

open class NativePlatformBuild(agent: Agent, buildReceiptSource: Boolean = false, init: BuildType.() -> Unit = {}) : BuildType({
    name = "Test on $agent"
    id = RelativeId("Build$agent")

    runOn(agent)

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        gradle {
            tasks = "clean build -PagentName=${agent.name}${agent.allPublishTasks}"
            if (buildReceiptSource) {
                gradleParams = "-PignoreIncomingBuildReceipt"
            }
            buildFile = ""
        }
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
        build/repo => repo
    """.trimIndent() + "\n$archiveReports"

    init(this)
})

class BuildTrigger(dependencies: List<BuildType>) : BuildType({
    name = "Run platform tests (Trigger)"
    type = Type.COMPOSITE

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

    features {
        publishCommitStatus()
    }
})
