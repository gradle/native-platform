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
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

class Publishing(buildAndTest: List<BuildType>, buildReceiptSource: BuildType) : Project({
    name = "Publishing"

    val nativeLibraryPublishingBuilds = Agent.values().map { agent ->
        NativeLibraryPublishSnapshot(agent, buildAndTest, buildReceiptSource).also(::buildType)
    }
    val publishApi = PublishJavaApiSnapshot(nativeLibraryPublishingBuilds, buildAndTest, buildReceiptSource).also(::buildType)

    buildTypesOrder = listOf(publishApi) + nativeLibraryPublishingBuilds
})

open class NativePlatformPublishSnapshot(uploadTasks: List<String>, buildAndTest: List<BuildType>, buildReceiptSource: BuildType, init: BuildType.() -> Unit) : BuildType({
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
                tasks = "clean $task $buildScanInit -Psnapshot -PonlyPrimaryVariants -PbintrayUserName=%ARTIFACTORY_USERNAME% -PbintrayApiKey=%ARTIFACTORY_PASSWORD%"
                buildFile = ""
            }
        }
    }

    dependencies {
        buildAndTest.forEach {
            snapshot(it) {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
        }
        dependency(buildReceiptSource) {
            artifacts {
                cleanDestination = true
                artifactRules = "$buildReceipt => incoming-distributions/"
            }
        }
    }

    features {
        publishCommitStatus()
    }

    init(this)
})

class NativeLibraryPublishSnapshot(agent: Agent, buildAndTest: List<BuildType>, buildReceiptSource: BuildType) :
    NativePlatformPublishSnapshot(listOf(":uploadJni"), buildAndTest, buildReceiptSource, {
        name = "Publish $agent snapshot"
        id = RelativeId("Publishing_Publish${agent}Snapshot")
        runOn(agent)
    })

class PublishJavaApiSnapshot(nativeLibraryPublishingBuilds: List<NativeLibraryPublishSnapshot>, buildAndTest: List<BuildType>, buildReceiptSource: BuildType) :
    NativePlatformPublishSnapshot(listOf(":uploadMain", ":testApp:uploadMain"), buildAndTest, buildReceiptSource, {
        name = "Publish Native Platform snapshot"
        id = RelativeId("Publishing_PublishJavaApiSnapshot")
        runOn(Agent.Linux)

        dependencies {
            nativeLibraryPublishingBuilds.forEach {
                snapshot(it) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
            }
        }
    })
