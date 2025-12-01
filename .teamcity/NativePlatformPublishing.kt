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
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import java.util.*

class Publishing(buildAndTest: List<BuildType>, buildReceiptSource: BuildType) : Project({
    name = "Publishing"

    ReleaseType.entries.forEach { releaseType ->
        val publishProject = NativePlatformPublishProject(releaseType, buildAndTest, buildReceiptSource)
        buildType(publishProject.publishApi)
        subProject(publishProject)
    }
})

private const val versionPostfixParameterName = "versionPostfix"

class NativePlatformPublishProject(releaseType: ReleaseType, buildAndTest: List<BuildType>, buildReceiptSource: BuildType) : Project({
    id = RelativeId("Publish${releaseType.name}")
    name = "Publish ${releaseType.name}"

    cleanup {
        keepRule {
            id = "KEEP_PUBLISHED_ARTIFACTS"
            keepAtLeast = builds(10)
            dataToKeep = everything()
            applyPerEachBranch = false
            preserveArtifactsDependencies = true
        }
    }
}) {
    private val nativeLibraryAllJniBuilds = agentsForAllNativePlatformJniPublications.map { agent ->
        NativeLibraryPublish(releaseType, agent, buildAndTest, buildReceiptSource).also(::buildType)
    }
    private val nativeLibraryNcursesJniBuilds = agentsForNcursesOnlyPublications.map { agent ->
        NativeLibraryPublishNcurses(releaseType, agent, buildAndTest, buildReceiptSource).also(::buildType)
    }
    val publishApi = PublishJavaApi(
        releaseType,
        agentForJavaPublication,
        nativeLibraryAllJniBuilds + nativeLibraryNcursesJniBuilds,
        buildAndTest,
        buildReceiptSource
    )
}

open class NativePlatformPublishSnapshot(
    agent: Agent,
    releaseType: ReleaseType,
    uploadTasks: List<String>,
    buildAndTest: List<BuildType>,
    buildReceiptSource: BuildType,
    init: BuildType.() -> Unit
) : BuildType({
    params {
        param("ARTIFACTORY_USERNAME", releaseType.username)
        password("ARTIFACTORY_PASSWORD", releaseType.password, display = ParameterDisplay.HIDDEN)
        if (releaseType.userProvidedVersion) {
            text("reverse.dep.*.$versionPostfixParameterName", "${releaseType.gradleProperty}-1", display = ParameterDisplay.PROMPT, allowEmpty = false)
        }
        param("env.GRADLE_INTERNAL_REPO_URL", "%gradle.internal.repository.url%")
        param("env.ORG_GRADLE_PROJECT_publishUserName", "%ARTIFACTORY_USERNAME%")
        param("env.ORG_GRADLE_PROJECT_publishApiKey", "%ARTIFACTORY_PASSWORD%")
        param("env.PGP_SIGNING_KEY", "%pgpSigningKey%")
        param("env.PGP_SIGNING_KEY_PASSPHRASE", "%pgpSigningPassphrase%")
    }

    vcs {
        root(DslContext.settingsRoot)
        cleanCheckout = true
    }

    steps {
        uploadTasks.forEach { task ->
            gradle {
                name = "Gradle $task"
                tasks =
                    "clean $task -P${releaseType.gradleProperty}${if (releaseType.userProvidedVersion) "=%versionPostfix%" else ""} ${
                        javaInstallationLocations(
                            agent
                        )
                    }"
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
        lowerRequiredFreeDiskSpace()
    }

    init(this)
})

class NativeLibraryPublish(
    releaseType: ReleaseType = ReleaseType.Snapshot,
    agent: Agent,
    buildAndTest: List<BuildType>,
    buildReceiptSource: BuildType
) :
    NativePlatformPublishSnapshot(
        agent,
        releaseType,
        listOf(agent.publishJniTasks.trim()),
        buildAndTest,
        buildReceiptSource,
        {
        val extraQualification = if (agent.os is Linux)
            "general and ${agent.os.ncurses.toString().lowercase(Locale.ROOT)} "
        else ""
        name = "Publish ${agent.os.osType} ${agent.architecture} $extraQualification${releaseType.name}"
        id = RelativeId("Publishing_Publish${agent.os.osType}${agent.architecture}${releaseType.name}")
        runOn(agent)
        artifactRules = """
            **/build/**/*.pdb
            **/build/libs/**
        """.trimIndent() + "\n$archiveReports"
    })

class NativeLibraryPublishNcurses(
    releaseType: ReleaseType = ReleaseType.Snapshot,
    agent: Agent,
    buildAndTest: List<BuildType>,
    buildReceiptSource: BuildType
) :
    NativePlatformPublishSnapshot(
        agent,
        releaseType,
        listOf(":native-platform:uploadNcursesJni"),
        buildAndTest,
        buildReceiptSource,
        {
        val linuxOs: Linux = agent.os as Linux
        name = "Publish ${linuxOs.osType} ${agent.architecture} ${linuxOs.ncurses.toString().lowercase(Locale.ROOT)} only ${releaseType.name}"
        id = RelativeId("Publishing_Publish${linuxOs.osType}${agent.architecture}${linuxOs.ncurses}${releaseType.name}")
        runOn(agent)
        artifactRules = archiveReports
    })

class PublishJavaApi(
    releaseType: ReleaseType = ReleaseType.Snapshot,
    agent: Agent,
    nativeLibraryPublishingBuilds: List<NativePlatformPublishSnapshot>,
    buildAndTest: List<BuildType>,
    buildReceiptSource: BuildType
) :
    NativePlatformPublishSnapshot(
        agent,
        releaseType,
        listOf(":native-platform:uploadMain", ":test-app:uploadMain"),
        buildAndTest,
        buildReceiptSource,
        {
            name = "Publish Native Platform ${releaseType.name}"
            id = RelativeId("Publishing_PublishJavaApi${releaseType.name}")
            runOn(Agent.UbuntuAmd64)
            artifactRules = archiveReports
            params {
                if (releaseType.userProvidedVersion) {
                    param(versionPostfixParameterName, "%reverse.dep.*.$versionPostfixParameterName%")
                }
            }

            dependencies {
                nativeLibraryPublishingBuilds.forEach {
                    snapshot(it) {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                    }
                }
            }
        })
